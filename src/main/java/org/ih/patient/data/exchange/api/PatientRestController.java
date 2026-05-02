package org.ih.patient.data.exchange.api;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ih.patient.data.exchange.api.dto.ForcePatientSyncRequest;
import org.ih.patient.data.exchange.api.dto.LocalMpiUpdateRequest;
import org.ih.patient.data.exchange.dataimports.ImportPatientService;
import org.ih.patient.data.exchange.domain.FhirResponse;
import org.ih.patient.data.exchange.domain.ImportResponse;
import org.ih.patient.data.exchange.dto.SearchPateintDTO;
import org.ih.patient.data.exchange.scheduler.DataSendToFHIR;
import org.ih.patient.data.exchange.scheduler.ResourceIsNotValid;
import org.ih.patient.data.exchange.mpiduplicate.ForceSyncDuplicateResolutionContext;
import org.ih.patient.data.exchange.mpiduplicate.MpiDuplicateReviewResolutionService;
import org.ih.patient.data.exchange.search.PatientSearchParam;
import org.ih.patient.data.exchange.search.PatientSearchService;
import org.ih.patient.data.exchange.service.BundleService;
import org.ih.patient.data.exchange.service.LocalMpiAlreadySetException;
import org.ih.patient.data.exchange.service.LocalPatientMpiUpdateService;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/patientinfo/rest/v1/patient")
public class PatientRestController {

	private static final Logger LOGGER = LoggerFactory.getLogger(PatientRestController.class);

	@Autowired
	private DataSendToFHIR dataSendToFHIR;

	@Autowired
	private LocalPatientMpiUpdateService localPatientMpiUpdateService;

	@Autowired
	private MpiDuplicateReviewResolutionService mpiDuplicateReviewResolutionService;

	@Autowired
	private ImportPatientService patientService;
	
	@Autowired
	private PatientSearchService patientSearchService;
	
	@Autowired
	private BundleService bundleService;

	@GetMapping("/import/{uuid}")
	public ResponseEntity<ImportResponse> reschedule(@PathVariable("uuid") String uuid)
			throws ParseException, JSONException, IOException {
		patientService.importPatient(uuid, "");
		ImportResponse res = new ImportResponse();
		res.setMessage("ok");
		res.setUuid(uuid);
		return new ResponseEntity<>(res, HttpStatus.OK);
	}

	@GetMapping("/import/{uuid}/{locationUUid}")
	public ResponseEntity<ImportResponse> patientImport(@PathVariable("uuid") String uuid,
			@PathVariable("locationUUid") String locationUUid) throws ParseException, JSONException, IOException {
		patientService.importPatient(uuid, locationUUid);
		ImportResponse res = new ImportResponse();
		res.setMessage("ok");
		res.setUuid(uuid);
		return new ResponseEntity<>(res, HttpStatus.OK);
	}

	@PostMapping("/search")
	public ResponseEntity<List<SearchPateintDTO>> searchPatient(@RequestBody PatientSearchParam param)
			throws UnsupportedEncodingException, ParseException, JSONException {
		return new ResponseEntity<>(patientSearchService.searchPatient(param), HttpStatus.OK);
	}

	/**
	 * Operator flow: export one Patient by OpenMRS UUID using the same steps as {@link DataSendToFHIR}
	 * scheduled export (local FHIR read-by-id → IG validation → MCI → sync identifiers back).
	 * Does <strong>not</strong> run central OpenCR demographic duplicate search or duplicate-review DB logic.
	 */
	@PostMapping(value = "/sync/force", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> forceSyncPatient(@RequestBody(required = false) ForcePatientSyncRequest body) {
		if (body == null || body.getPatientUuid() == null || body.getPatientUuid().trim().isEmpty()) {
			return ResponseEntity.badRequest().body(errorBody("patientUuid is required"));
		}
		if (body.getResolvedBy() == null || body.getResolvedBy().trim().isEmpty()) {
			return ResponseEntity.badRequest().body(errorBody("resolvedBy is required"));
		}
		ForceSyncDuplicateResolutionContext.begin(body.getResolvedBy());
		try {
			FhirResponse res = dataSendToFHIR.forceSendPatientToCentralByUuid(body.getPatientUuid().trim());
			Map<String, Object> ok = new LinkedHashMap<>();
			ok.put("patientUuid", body.getPatientUuid().trim());
			ok.put("statusCode", res.getStatusCode());
			ok.put("message", res.getMessage());
			ok.put("response", res.getResponse());
			if ("skipped".equals(res.getStatusCode())) {
				ok.put("status", "skipped");
			}
			return ResponseEntity.ok(ok);
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
		} catch (ResourceIsNotValid ex) {
			return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorBody(ex.getMessage()));
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(ex.getMessage()));
		} finally {
			ForceSyncDuplicateResolutionContext.end();
		}
	}

	/**
	 * Operator flow: set or replace the MPI identifier on the local OpenMRS FHIR Patient (no call to MCI).
	 */
	@PostMapping(value = "/mpi/local", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> updateLocalMpi(@RequestBody(required = false) LocalMpiUpdateRequest body)
			throws UnsupportedEncodingException {
		if (body == null || body.getPatientUuid() == null || body.getPatientUuid().trim().isEmpty()) {
			return ResponseEntity.badRequest().body(errorBody("patientUuid is required"));
		}
		if (body.getMpiIdentifierValue() == null || body.getMpiIdentifierValue().trim().isEmpty()) {
			return ResponseEntity.badRequest().body(errorBody("mpiIdentifierValue is required"));
		}
		if (body.getResolvedBy() == null || body.getResolvedBy().trim().isEmpty()) {
			return ResponseEntity.badRequest().body(errorBody("resolvedBy is required"));
		}
		try {
			localPatientMpiUpdateService.applyMpiIdentifierToLocalPatient(body.getPatientUuid().trim(),
					body.getMpiIdentifierValue().trim());
			String resolvedBy = body.getResolvedBy().trim();
			try {
				mpiDuplicateReviewResolutionService.resolvePendingCaseAfterLocalMpiUpdate(
						body.getPatientUuid().trim(),
						body.getMpiIdentifierValue().trim(),
						body.getChosenFhirPatientLogicalId(),
						resolvedBy);
			} catch (RuntimeException ex) {
				LOGGER.warn(
						"Duplicate-review resolution after local MPI failed for patient {}: {}",
						body.getPatientUuid(), ex.getMessage(), ex);
			}
			Map<String, Object> ok = new LinkedHashMap<>();
			ok.put("status", "ok");
			ok.put("patientUuid", body.getPatientUuid().trim());
			ok.put("mpiIdentifierValue", body.getMpiIdentifierValue().trim());
			return ResponseEntity.ok(ok);
		} catch (LocalMpiAlreadySetException ex) {
			Map<String, Object> skipped = new LinkedHashMap<>();
			skipped.put("status", "skipped");
			skipped.put("patientUuid", body.getPatientUuid().trim());
			skipped.put("message", ex.getMessage());
			return ResponseEntity.ok(skipped);
		} catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
		} catch (IllegalStateException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ex.getMessage()));
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(ex.getMessage()));
		}
	}

	private static Map<String, Object> errorBody(String message) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("error", message);
		return m;
	}

	/** Same contract as MCI {@code GET /mci/rest/v1/patient/search} — arbitrary Patient query params against OpenCR. */
	@GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> searchPatientOpenCr(@RequestParam Map<String, String> reqParam)
			throws UnsupportedEncodingException, ParseException, JSONException {
		return new ResponseEntity<>(bundleService.search("Patient", reqParam), HttpStatus.OK);
	}

	@GetMapping(value = "/{resourceType}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> searchPatient(
			@PathVariable("resourceType") String resourceType,
			@RequestParam Map<String, String> reqParam)
			throws UnsupportedEncodingException, ParseException, JSONException {

		return new ResponseEntity<>(
				bundleService.search(resourceType, reqParam), HttpStatus.OK);
	}

}
