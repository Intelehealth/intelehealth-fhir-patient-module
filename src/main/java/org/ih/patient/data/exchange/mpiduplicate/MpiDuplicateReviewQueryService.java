package org.ih.patient.data.exchange.mpiduplicate;

import java.util.List;
import java.util.stream.Collectors;

import org.ih.patient.data.exchange.api.dto.MpiDuplicateReviewCandidateDto;
import org.ih.patient.data.exchange.api.dto.MpiDuplicateReviewCaseSummaryDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MpiDuplicateReviewQueryService {

	@Autowired
	private MpiPatientDuplicateReviewCaseRepository caseRepository;

	@Autowired
	private MpiPatientDuplicateReviewCandidateRepository candidateRepository;

	@Transactional(readOnly = true)
	public List<MpiDuplicateReviewCaseSummaryDto> listPendingCases() {
		return caseRepository.findByReviewStatusOrderByDateCreatedDesc(MpiDuplicateReviewStatus.PENDING).stream()
				.map(this::toSummary)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<MpiDuplicateReviewCandidateDto> listCandidatesForCaseUuid(String caseUuid) {
		return caseRepository.findByCaseUuid(caseUuid)
				.map(c -> candidateRepository.findByReviewCase_IdOrderByIdAsc(c.getId()).stream()
						.map(this::toCandidate)
						.collect(Collectors.toList()))
				.orElseThrow(() -> new IllegalArgumentException("Unknown caseUuid: " + caseUuid));
	}

	private MpiDuplicateReviewCaseSummaryDto toSummary(MpiPatientDuplicateReviewCase c) {
		MpiDuplicateReviewCaseSummaryDto d = new MpiDuplicateReviewCaseSummaryDto();
		d.setId(c.getId());
		d.setCaseUuid(c.getCaseUuid());
		d.setLocalPatientUuid(c.getLocalPatientUuid());
		d.setSourceBirthdate(c.getSourceBirthdate());
		d.setSourceFamily(c.getSourceFamily());
		d.setSourceGiven(c.getSourceGiven());
		d.setSourceGenderCode(c.getSourceGenderCode());
		d.setSourceTelecom(c.getSourceTelecom());
		d.setCandidateCount(c.getCandidateCount());
		d.setReviewStatus(c.getReviewStatus() != null ? c.getReviewStatus().name() : null);
		d.setDateCreated(c.getDateCreated());
		return d;
	}

	private MpiDuplicateReviewCandidateDto toCandidate(MpiPatientDuplicateReviewCandidate c) {
		MpiDuplicateReviewCandidateDto d = new MpiDuplicateReviewCandidateDto();
		d.setId(c.getId());
		d.setFhirPatientLogicalId(c.getFhirPatientLogicalId());
		d.setMpiIdentifierValue(c.getMpiIdentifierValue());
		d.setCandidateBirthdate(c.getCandidateBirthdate());
		d.setCandidateFamily(c.getCandidateFamily());
		d.setCandidateGiven(c.getCandidateGiven());
		d.setCandidateGenderCode(c.getCandidateGenderCode());
		d.setCandidateTelecom(c.getCandidateTelecom());
		d.setCandidateAddressCity(c.getCandidateAddressCity());
		d.setCandidateAddressSnapshot(truncate(c.getCandidateAddressSnapshot(), 500));
		return d;
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return null;
		}
		return s.length() <= max ? s : s.substring(0, max) + "...";
	}
}
