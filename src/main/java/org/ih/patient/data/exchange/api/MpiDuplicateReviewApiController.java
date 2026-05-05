package org.ih.patient.data.exchange.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ih.patient.data.exchange.api.dto.MpiDuplicateReviewCandidateDto;
import org.ih.patient.data.exchange.api.dto.MpiDuplicateReviewCaseSummaryDto;
import org.ih.patient.data.exchange.mpiduplicate.MpiDuplicateReviewQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/patientinfo/rest/v1/mpi-duplicate-review")
public class MpiDuplicateReviewApiController {

	@Autowired
	private MpiDuplicateReviewQueryService mpiDuplicateReviewQueryService;

	/** All source patients (review cases) currently in {@code PENDING} status. */
	@GetMapping(value = "/cases/pending", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<MpiDuplicateReviewCaseSummaryDto>> pendingCases() {
		return ResponseEntity.ok(mpiDuplicateReviewQueryService.listPendingCases());
	}

	/** Duplicate MPI candidates for one review case (by {@code case_uuid}). */
	@GetMapping(value = "/cases/{caseUuid}/candidates", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> candidatesForCase(@PathVariable("caseUuid") String caseUuid) {
		try {
			return ResponseEntity.ok(mpiDuplicateReviewQueryService.listCandidatesForCaseUuid(caseUuid.trim()));
		} catch (IllegalArgumentException ex) {
			Map<String, Object> err = new LinkedHashMap<>();
			err.put("error", ex.getMessage());
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
		}
	}
}
