package com.example.requests.infrastructure.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestApiIntegrationTest {

	private static final String BASE = "/api/requests";

	@Autowired
	private MockMvc mvc;

	@Nested
	class Create {

		@Test
		void returns201WithCreatedRequest() throws Exception {
			String json = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
							.content("{\"name\":\"  Grant  \",\"content\":\"Body\"}"))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.id").isNumber())
					.andExpect(jsonPath("$.name").value("Grant"))
					.andExpect(jsonPath("$.content").value("Body"))
					.andExpect(jsonPath("$.state").value("CREATED"))
					.andExpect(jsonPath("$.publicationNumber").value(nullValue()))
					.andExpect(jsonPath("$.allowedActions", contains("VERIFY", "DELETE")))
					.andExpect(jsonPath("$.contentEditable").value(true))
					.andExpect(jsonPath("$.createdAt").value(notNullValue()))
					.andReturn().getResponse().getContentAsString();
			long id = ((Number) JsonPath.read(json, "$.id")).longValue();

			mvc.perform(get(BASE + "/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Grant"));
		}

		@ParameterizedTest
		@ValueSource(strings = {
				"{\"content\":\"Body\"}",
				"{\"name\":\"\",\"content\":\"Body\"}",
				"{\"name\":\"   \",\"content\":\"Body\"}",
				"{\"name\":null,\"content\":\"Body\"}"})
		void requiresName(String body) throws Exception {
			mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").value("Validation failed"))
					.andExpect(jsonPath("$.errors.name").value("must not be blank"));
		}

		@ParameterizedTest
		@ValueSource(strings = {
				"{\"name\":\"Grant\"}",
				"{\"name\":\"Grant\",\"content\":\"\"}",
				"{\"name\":\"Grant\",\"content\":\" \\n \"}"})
		void requiresContent(String body) throws Exception {
			mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.content").value("must not be blank"));
		}

		@Test
		void reportsAllInvalidFieldsAtOnce() throws Exception {
			mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.name").exists())
					.andExpect(jsonPath("$.errors.content").exists());
		}

		@Test
		void rejectsTooLongName() throws Exception {
			mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
							.content("{\"name\":\"" + "n".repeat(256) + "\",\"content\":\"Body\"}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.name").value(containsString("255")));
		}

		@Test
		void rejectsMalformedJson() throws Exception {
			mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
					.andExpect(status().isBadRequest());
		}

		@Test
		void rejectsMissingBody() throws Exception {
			mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isBadRequest());
		}

		@Test
		void rejectsNonJsonContentType() throws Exception {
			mvc.perform(post(BASE).contentType(MediaType.TEXT_PLAIN).content("name=Grant"))
					.andExpect(status().isUnsupportedMediaType());
		}
	}

	@Nested
	class Read {

		@Test
		void returnsRequestById() throws Exception {
			long id = create("Grant");

			mvc.perform(get(BASE + "/" + id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(id))
					.andExpect(jsonPath("$.name").value("Grant"));
		}

		@Test
		void unknownIdIs404ProblemDetail() throws Exception {
			mvc.perform(get(BASE + "/999999"))
					.andExpect(status().isNotFound())
					.andExpect(header().string("Content-Type", containsString("application/problem+json")))
					.andExpect(jsonPath("$.title").value("Request not found"))
					.andExpect(jsonPath("$.detail").value("Request 999999 not found"));
		}

		@Test
		void nonNumericIdIs400() throws Exception {
			mvc.perform(get(BASE + "/abc")).andExpect(status().isBadRequest());
		}
	}

	@Nested
	class EditContent {

		@Test
		void editsInCreated() throws Exception {
			long id = create("Grant");

			editContent(id, "Updated").andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Updated"));
		}

		@Test
		void editsInVerified() throws Exception {
			long id = create("Grant");
			act(id, "verify");

			editContent(id, "Updated").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("VERIFIED"));
		}

		@Test
		void refusesInAccepted() throws Exception {
			long id = create("Grant");
			act(id, "verify");
			act(id, "accept");

			editContent(id, "Updated")
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.title").value("Content not editable"))
					.andExpect(jsonPath("$.detail").value("Content cannot be edited in state ACCEPTED"))
					.andExpect(jsonPath("$.currentState").value("ACCEPTED"));
			mvc.perform(get(BASE + "/" + id)).andExpect(jsonPath("$.content").value("Body"));
		}

		@Test
		void refusesInPublished() throws Exception {
			long id = publish(create("Grant"));

			editContent(id, "Updated").andExpect(status().isConflict());
		}

		@Test
		void refusesInRejectedAndDeleted() throws Exception {
			long rejected = create("Rejected");
			act(rejected, "verify");
			actWithReason(rejected, "reject", "no");
			long deleted = create("Deleted");
			actWithReason(deleted, "delete", "no");

			editContent(rejected, "Updated").andExpect(status().isConflict());
			editContent(deleted, "Updated").andExpect(status().isConflict());
		}

		@Test
		void requiresContent() throws Exception {
			long id = create("Grant");

			mvc.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\" \"}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.content").exists());
		}

		@Test
		void nameIsNotChangedEvenIfSent() throws Exception {
			long id = create("Grant");

			mvc.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
							.content("{\"name\":\"Hacked\",\"content\":\"Updated\"}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.name").value("Grant"));
		}

		@Test
		void unknownIdIs404() throws Exception {
			editContent(999_999, "Updated").andExpect(status().isNotFound());
		}
	}

	@Nested
	class Lifecycle {

		@Test
		void happyPathToPublishedAssignsPublicationNumber() throws Exception {
			long id = create("Grant");

			act(id, "verify").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("VERIFIED"))
					.andExpect(jsonPath("$.allowedActions", contains("ACCEPT", "REJECT")));
			act(id, "accept").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACCEPTED"))
					.andExpect(jsonPath("$.contentEditable").value(false));
			act(id, "publish").andExpect(status().isOk())
					.andExpect(jsonPath("$.state").value("PUBLISHED"))
					.andExpect(jsonPath("$.publicationNumber").isNumber())
					.andExpect(jsonPath("$.allowedActions", empty()));
		}

		@Test
		void publishedRequestsGetDistinctIncreasingNumbers() throws Exception {
			long first = publicationNumberOf(publish(create("First")));
			long second = publicationNumberOf(publish(create("Second")));

			assertThat(second).isGreaterThan(first);
		}

		@Test
		void rejectFromVerifiedStoresReason() throws Exception {
			long id = create("Grant");
			act(id, "verify");

			actWithReason(id, "reject", "Out of scope")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.state").value("REJECTED"))
					.andExpect(jsonPath("$.reason").value("Out of scope"));
		}

		@Test
		void rejectFromAccepted() throws Exception {
			long id = create("Grant");
			act(id, "verify");
			act(id, "accept");

			actWithReason(id, "reject", "Budget cut").andExpect(jsonPath("$.state").value("REJECTED"));
		}

		@Test
		void deleteFromCreatedIsSoftDelete() throws Exception {
			long id = create("Grant");

			actWithReason(id, "delete", "Duplicate").andExpect(status().isOk())
					.andExpect(jsonPath("$.state").value("DELETED"));
			mvc.perform(get(BASE + "/" + id)).andExpect(status().isOk())
					.andExpect(jsonPath("$.reason").value("Duplicate"));
		}

		@ParameterizedTest
		@ValueSource(strings = {"reject", "delete"})
		void reasonIsMandatory(String action) throws Exception {
			long id = create("Grant");
			if (action.equals("reject")) {
				act(id, "verify");
			}

			mvc.perform(post(BASE + "/" + id + "/" + action)).andExpect(status().isBadRequest());
			mvc.perform(post(BASE + "/" + id + "/" + action).contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
			actWithReason(id, action, "   ").andExpect(status().isBadRequest());
			actWithReason(id, action, "r".repeat(1001)).andExpect(status().isBadRequest());
		}

		@Test
		void illegalTransitionsAre409() throws Exception {
			long id = create("Grant");

			act(id, "accept").andExpect(status().isConflict())
					.andExpect(jsonPath("$.title").value("Invalid state transition"))
					.andExpect(jsonPath("$.currentState").value("CREATED"))
					.andExpect(jsonPath("$.action").value("ACCEPT"));
			act(id, "publish").andExpect(status().isConflict());
			actWithReason(id, "reject", "no").andExpect(status().isConflict());
		}

		@Test
		void cannotSkipVerificationOrRepeatAStep() throws Exception {
			long id = create("Grant");
			act(id, "verify");

			act(id, "verify").andExpect(status().isConflict());
			actWithReason(id, "delete", "too late").andExpect(status().isConflict());
		}

		@Test
		void terminalStatesAllowNothing() throws Exception {
			long published = publish(create("Published"));
			long deleted = create("Deleted");
			actWithReason(deleted, "delete", "dup");

			act(published, "verify").andExpect(status().isConflict());
			actWithReason(published, "reject", "late").andExpect(status().isConflict());
			act(published, "publish").andExpect(status().isConflict());
			act(deleted, "verify").andExpect(status().isConflict());
			actWithReason(deleted, "delete", "again").andExpect(status().isConflict());
		}

		@Test
		void failedRepublishKeepsOriginalNumber() throws Exception {
			long id = publish(create("Grant"));
			long number = publicationNumberOf(id);

			act(id, "publish").andExpect(status().isConflict());

			assertThat(publicationNumberOf(id)).isEqualTo(number);
		}

		@Test
		void transitionOnUnknownIdIs404() throws Exception {
			act(999_999, "verify").andExpect(status().isNotFound());
			actWithReason(999_999, "delete", "x").andExpect(status().isNotFound());
		}

		@Test
		void unknownActionIsNotAnEndpoint() throws Exception {
			long id = create("Grant");

			act(id, "approve").andExpect(status().isNotFound());
		}
	}

	@Nested
	class History {

		@Test
		void recordsEveryStateChangeInOrder() throws Exception {
			long id = create("Grant");
			act(id, "verify");
			editContent(id, "Updated");
			actWithReason(id, "reject", "Incomplete");

			mvc.perform(get(BASE + "/" + id + "/history"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(3)))
					.andExpect(jsonPath("$[0].action").value("CREATE"))
					.andExpect(jsonPath("$[0].fromState").value(nullValue()))
					.andExpect(jsonPath("$[0].toState").value("CREATED"))
					.andExpect(jsonPath("$[1].action").value("VERIFY"))
					.andExpect(jsonPath("$[1].fromState").value("CREATED"))
					.andExpect(jsonPath("$[2].action").value("REJECT"))
					.andExpect(jsonPath("$[2].toState").value("REJECTED"))
					.andExpect(jsonPath("$[2].reason").value("Incomplete"))
					.andExpect(jsonPath("$[2].occurredAt").value(notNullValue()));
		}

		@Test
		void failedTransitionsAreNotRecorded() throws Exception {
			long id = create("Grant");
			act(id, "publish");

			mvc.perform(get(BASE + "/" + id + "/history")).andExpect(jsonPath("$", hasSize(1)));
		}

		@Test
		void unknownIdIs404() throws Exception {
			mvc.perform(get(BASE + "/999999/history")).andExpect(status().isNotFound());
		}
	}

	@Nested
	class Browse {

		@Test
		void defaultPageSizeIsTen() throws Exception {
			createMany("Paged", 12);

			mvc.perform(get(BASE).param("name", "Paged"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items", hasSize(10)))
					.andExpect(jsonPath("$.items[0].name").value("Paged 01"))
					.andExpect(jsonPath("$.page").value(0))
					.andExpect(jsonPath("$.size").value(10))
					.andExpect(jsonPath("$.totalItems").value(12))
					.andExpect(jsonPath("$.totalPages").value(2));
		}

		@Test
		void secondPageHoldsTheRemainder() throws Exception {
			createMany("Paged", 12);

			mvc.perform(get(BASE).param("name", "Paged").param("page", "1"))
					.andExpect(jsonPath("$.items", hasSize(2)))
					.andExpect(jsonPath("$.items[0].name").value("Paged 11"));
		}

		@Test
		void pageBeyondTheEndIsEmptyNotAnError() throws Exception {
			createMany("Paged", 3);

			mvc.perform(get(BASE).param("name", "Paged").param("page", "7"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items", empty()))
					.andExpect(jsonPath("$.totalItems").value(3));
		}

		@Test
		void customSizeIsHonouredAndCappedAtMaximum() throws Exception {
			createMany("Paged", 3);

			mvc.perform(get(BASE).param("name", "Paged").param("size", "2"))
					.andExpect(jsonPath("$.items", hasSize(2)))
					.andExpect(jsonPath("$.totalPages").value(2));
			mvc.perform(get(BASE).param("size", "100000"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.size").value(100));
		}

		@ParameterizedTest
		@ValueSource(strings = {"size=0", "size=-1", "page=-1", "size=abc", "page=x"})
		void invalidPagingParametersAre400(String query) throws Exception {
			String[] parts = query.split("=");
			mvc.perform(get(BASE).param(parts[0], parts[1])).andExpect(status().isBadRequest());
		}

		@Test
		void invalidPagingReportsTheParameter() throws Exception {
			mvc.perform(get(BASE).param("size", "0"))
					.andExpect(jsonPath("$.errors.size").exists());
		}

		@Test
		void filtersByNameCaseInsensitiveContains() throws Exception {
			create("Solar Panels");
			create("solar roof");
			create("Wind farm");

			mvc.perform(get(BASE).param("name", "SOLAR"))
					.andExpect(jsonPath("$.items[*].name", contains("Solar Panels", "solar roof")));
		}

		@Test
		void filtersByState() throws Exception {
			long verified = create("Filter verified");
			act(verified, "verify");
			create("Filter created");

			mvc.perform(get(BASE).param("name", "Filter").param("state", "VERIFIED"))
					.andExpect(jsonPath("$.items[*].name", contains("Filter verified")));
		}

		@Test
		void unknownStateIs400() throws Exception {
			mvc.perform(get(BASE).param("state", "UNKNOWN")).andExpect(status().isBadRequest());
		}

		@Test
		void blankNameFilterMeansNoFilter() throws Exception {
			create("Anything");

			mvc.perform(get(BASE).param("name", "  "))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.totalItems").value(greaterThanOrEqualTo(1)));
		}

		@Test
		void noMatchesGiveEmptyPage() throws Exception {
			mvc.perform(get(BASE).param("name", "does-not-exist"))
					.andExpect(jsonPath("$.items", empty()))
					.andExpect(jsonPath("$.totalItems").value(0))
					.andExpect(jsonPath("$.totalPages").value(0));
		}
	}

	@Test
	void openApiDocumentIsServed() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/requests/{id}/publish']").exists());
	}

	private long create(String name) throws Exception {
		String json = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\",\"content\":\"Body\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	private void createMany(String prefix, int count) throws Exception {
		for (int i = 1; i <= count; i++) {
			create("%s %02d".formatted(prefix, i));
		}
	}

	private ResultActions act(long id, String action) throws Exception {
		return mvc.perform(post(BASE + "/" + id + "/" + action));
	}

	private ResultActions actWithReason(long id, String action, String reason) throws Exception {
		return mvc.perform(post(BASE + "/" + id + "/" + action).contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"" + reason + "\"}"));
	}

	private ResultActions editContent(long id, String content) throws Exception {
		return mvc.perform(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"" + content + "\"}"));
	}

	private long publish(long id) throws Exception {
		act(id, "verify").andExpect(status().isOk());
		act(id, "accept").andExpect(status().isOk());
		act(id, "publish").andExpect(status().isOk());
		return id;
	}

	private long publicationNumberOf(long id) throws Exception {
		String json = mvc.perform(get(BASE + "/" + id)).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.publicationNumber")).longValue();
	}
}
