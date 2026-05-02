package org.ih.patient.data.exchange.search;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Map;

import org.hl7.fhir.r4.model.Patient;
import org.ih.patient.data.exchange.config.FhirConfig;
import org.ih.patient.data.exchange.service.BundleService;
import org.ih.patient.data.exchange.utils.HttpWebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Single entry point for central OpenCR Patient searches (everything that lived in MCI
 * {@code PatientSearchImpl}, bundle generic search, and legacy HTTP demographic search).
 */
@Service
public class CentralPatientSearchService {

	@Autowired
	private FhirConfig fhirConfig;

	@Autowired
	private BundleService bundleService;

	@Value("${mpi.duplicate-precheck.search-result-count:50}")
	private int duplicatePrecheckSearchResultCount;

	/**
	 * FHIR generic search: {@code Patient?}&lt;encoded query&gt; via HAPI client (same as MCI {@code PatientSearchImpl}).
	 */
	public String searchPatientJson(Map<String, String> queryParameters) {
		return bundleService.search("Patient", queryParameters);
	}

	/**
	 * Legacy HTTP demographic search against {@link FhirConfig#getOpencrOpenhimURL()} {@code /Patient},
	 * matching old MCI {@code HttpWebClient.searchPatient} + {@code makeQueryParam} behaviour.
	 */
	public String searchPatientJsonByDemographics(Patient patient) throws UnsupportedEncodingException {
		String[] creds = fhirConfig.getOpenCRCredentials();
		if (creds.length < 2) {
			throw new IllegalStateException("OpenCR credentials not configured");
		}
		String patientBase = fhirConfig.getOpencrOpenhimURL() + "/Patient";
		URI uri = PatientDemographicSearchUriBuilder.buildUri(patientBase, patient,
				duplicatePrecheckSearchResultCount);
		return HttpWebClient.searchPatient(patientBase, uri, creds[0], creds[1]);
	}
}
