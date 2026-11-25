package com.mozip.domain.user.entity;

import java.time.LocalDate;

import com.mozip.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 활동 경험 (자기소개서 등에서 보여줄 경력)
@Getter
@Entity
@Table(name = "experiences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Experience extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	// 활동명
	@Column(nullable = false, length = 100)
	private String title;

	@Column(length = 50)
	private String role;

	@Column(length = 200)
	private String achievement;

	private LocalDate startedOn;

	private LocalDate endedOn;

	private Experience(User user, String title, String role, String achievement, LocalDate startedOn,
		LocalDate endedOn) {
		this.user = user;
		this.title = title;
		this.role = role;
		this.achievement = achievement;
		this.startedOn = startedOn;
		this.endedOn = endedOn;
	}

	public static Experience create(User user, String title, String role, String achievement, LocalDate startedOn,
		LocalDate endedOn) {
		return new Experience(user, title, role, achievement, startedOn, endedOn);
	}

}
