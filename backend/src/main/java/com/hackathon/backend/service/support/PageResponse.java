package com.hackathon.backend.service.support;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.hackathon.backend.exception.ApiException;
import com.hackathon.backend.exception.ErrorCode;

/**
 * List response {@code {items, page, pageSize, total}} and the paging rules of
 * architecture/SPEC.md §8: {@code page} is 1-based, {@code pageSize} default 20, max 100,
 * {@code sort} is {@code field} or {@code -field} from an allow-list.
 */
public record PageResponse<T>(List<T> items, int page, int pageSize, long total) {

	public static final int DEFAULT_SIZE = 20;

	public static final int MAX_SIZE = 100;

	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber() + 1,
				page.getSize(), page.getTotalElements());
	}

	public static <T> PageResponse<T> ofList(List<T> all, int page, int pageSize) {
		int from = Math.min((page - 1) * pageSize, all.size());
		int to = Math.min(from + pageSize, all.size());
		return new PageResponse<>(all.subList(from, to), page, pageSize, all.size());
	}

	public static Pageable pageable(Integer page, Integer pageSize, String sort, Set<String> allowedSort,
			Sort defaultSort) {
		int p = page == null ? 1 : page;
		int size = pageSize == null ? DEFAULT_SIZE : pageSize;
		if (p < 1 || size < 1 || size > MAX_SIZE) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must be >= 1 and pageSize between 1 and 100");
		}
		Sort s = defaultSort;
		if (sort != null && !sort.isBlank()) {
			boolean desc = sort.startsWith("-");
			String field = desc ? sort.substring(1) : sort;
			if (!allowedSort.contains(field)) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED, "sort must be one of " + allowedSort);
			}
			s = desc ? Sort.by(field).descending() : Sort.by(field).ascending();
		}
		return PageRequest.of(p - 1, size, s);
	}

	public static int[] check(Integer page, Integer pageSize) {
		pageable(page, pageSize, null, Set.of(), Sort.unsorted());
		return new int[] { page == null ? 1 : page, pageSize == null ? DEFAULT_SIZE : pageSize };
	}

}
