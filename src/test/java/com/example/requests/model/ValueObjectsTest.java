package com.example.requests.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

	@ParameterizedTest(name = "{0} items / size {1} = {2} pages")
	@CsvSource({"0,10,0", "1,10,1", "10,10,1", "11,10,2", "25,10,3"})
	void computesTotalPages(long totalItems, int size, int expectedPages) {
		assertThat(new PageResult<>(List.of(), 0, size, totalItems).totalPages()).isEqualTo(expectedPages);
	}

	@Test
	void mapsPageItemsAndKeepsMetadata() {
		PageResult<String> mapped = new PageResult<>(List.of(1, 2), 1, 2, 4).map(String::valueOf);

		assertThat(mapped).isEqualTo(new PageResult<>(List.of("1", "2"), 1, 2, 4));
	}

	@Test
	void pageResultIsADefensiveCopy() {
		List<Integer> items = new ArrayList<>(List.of(1));
		PageResult<Integer> page = new PageResult<>(items, 0, 10, 1);

		items.add(2);

		assertThat(page.items()).containsExactly(1);
	}

	@Test
	void pageQueryRejectsInvalidValues() {
		assertThatThrownBy(() -> new PageQuery(-1, 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PageQuery(0, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThat(new PageQuery(0, 1)).isNotNull();
	}

	@Test
	void filterTreatsBlankNameAsNoFilterAndTrims() {
		assertThat(new RequestFilter("  ", null).name()).isNull();
		assertThat(new RequestFilter(null, null).name()).isNull();
		assertThat(new RequestFilter("  grant ", RequestState.CREATED))
				.isEqualTo(new RequestFilter("grant", RequestState.CREATED));
	}

	static Stream<Arguments> valueObjects() {
		Instant at = Instant.parse("2026-01-01T10:00:00Z");
		return Stream.of(
				Arguments.of(new PageQuery(1, 10), new PageQuery(1, 10), new PageQuery(2, 10)),
				Arguments.of(new PageResult<>(List.of("a"), 0, 10, 1), new PageResult<>(List.of("a"), 0, 10, 1),
						new PageResult<>(List.of("b"), 0, 10, 1)),
				Arguments.of(new RequestFilter("grant", RequestState.CREATED),
						new RequestFilter("grant", RequestState.CREATED), new RequestFilter("grant", null)),
				Arguments.of(new StateChange(1L, RequestAction.REJECT, RequestState.VERIFIED, RequestState.REJECTED, "no", at),
						new StateChange(1L, RequestAction.REJECT, RequestState.VERIFIED, RequestState.REJECTED, "no", at),
						new StateChange(1L, RequestAction.REJECT, RequestState.VERIFIED, RequestState.REJECTED, "other", at)));
	}

	@ParameterizedTest
	@MethodSource("valueObjects")
	void valueObjectsHaveValueSemantics(Object value, Object equal, Object different) {
		assertThat(value).isEqualTo(value).isEqualTo(equal).hasSameHashCodeAs(equal)
				.isNotEqualTo(different).isNotEqualTo(null).isNotEqualTo("other type");
		assertThat(value.toString()).startsWith(value.getClass().getSimpleName() + "[").isEqualTo(equal.toString());
	}
}
