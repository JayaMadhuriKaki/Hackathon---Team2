package com.hackathon.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-year counters for claim and batch numbers (key such as CLM-2026 or PB-2026). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "display_counters")
public class DisplayCounter {

	@Id
	@Column(name = "counter_key", length = 20)
	private String counterKey;

	@Column(name = "last_value", nullable = false)
	private int lastValue;

	public DisplayCounter(String counterKey) {
		this.counterKey = counterKey;
	}

}
