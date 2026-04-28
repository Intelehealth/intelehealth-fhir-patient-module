package org.ih.patient.data.exchange.importupload;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.ih.patient.data.exchange.config.FhirConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;

@Service
public class PatientUploadImportService {

	private static final String OPENMRS_IDENTIFIER_LOCATION_EXTENSION_URL = "http://fhir.openmrs.org/ext/patient/identifier#location";
	private static final String OPENMRS_IDENTIFIER_PREFERRED_EXTENSION_URL = "http://fhir.openmrs.org/ext/patient/identifier#preferred";
	private static final String OPENMRS_DEFAULT_IDENTIFIER_LOCATION_UUID = "8d6c993e-c2cc-11de-8d13-0010c6dffd0f";

	private final FhirContext fhirContext = FhirContext.forR4();

	@Value("${patient.import.preferred.identifier.type.uuid}")
	private String preferredIdentifierTypeUuid;

	@Value("${patient.import.preferred.identifier.type.name:OpenEMPI ID}")
	private String preferredIdentifierTypeName;

	@Autowired
	private FhirConfig fhirConfig;

	public PatientUploadImportResponse importPatientFile(MultipartFile file) throws Exception {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("File is empty");
		}

		String content = new String(file.getBytes(), StandardCharsets.UTF_8);
		List<Patient> patients = parsePatients(content);

		PatientUploadImportResponse response = new PatientUploadImportResponse();
		response.setTotal(patients.size());

		for (Patient patient : patients) {
			PatientUploadImportItemResult item = new PatientUploadImportItemResult();
			item.setInputId(patient.getIdElement() != null ? patient.getIdElement().getIdPart() : null);
			try {
				ensurePreferredOpenEmiIdentifier(patient);
				ensureIdentifierLocation(patient);
				if (existsByIdentifier(patient)) {
					item.setStatus("SKIPPED");
					item.setMessage("Patient already exists by identifier");
					response.setSkipped(response.getSkipped() + 1);
				} else {
					patient.setId((String) null);
					MethodOutcome outcome = fhirConfig.getLocalOpenMRSFhirContext().create().resource(patient).execute();
					item.setStatus("CREATED");
					item.setCreatedId(outcome.getId() != null ? outcome.getId().getIdPart() : null);
					item.setMessage("Patient created");
					response.setCreated(response.getCreated() + 1);
				}
			} catch (Exception e) {
				item.setStatus("FAILED");
				item.setMessage(e.getMessage());
				response.setFailed(response.getFailed() + 1);
			}
			response.getItems().add(item);
		}
		return response;
	}

	private List<Patient> parsePatients(String content) {
		Resource parsed = (Resource) fhirContext.newJsonParser().parseResource(content);
		List<Patient> patients = new ArrayList<>();
		if (parsed instanceof Patient) {
			patients.add((Patient) parsed);
			return patients;
		}
		if (parsed instanceof Bundle) {
			Bundle bundle = (Bundle) parsed;
			for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
				if (entry.getResource() instanceof Patient) {
					patients.add((Patient) entry.getResource());
				}
			}
			return patients;
		}
		throw new IllegalArgumentException("Unsupported resource type. Upload Patient or Bundle JSON.");
	}

	private void ensureIdentifierLocation(Patient patient) {
		if (patient == null || patient.getIdentifier() == null) {
			return;
		}
		for (Identifier identifier : patient.getIdentifier()) {
			boolean hasLocation = identifier.getExtension().stream()
					.anyMatch(ext -> OPENMRS_IDENTIFIER_LOCATION_EXTENSION_URL.equals(ext.getUrl()));
			if (hasLocation) {
				continue;
			}
			Extension locationExtension = new Extension();
			locationExtension.setUrl(OPENMRS_IDENTIFIER_LOCATION_EXTENSION_URL);
			locationExtension.setValue(new Reference("Location/" + OPENMRS_DEFAULT_IDENTIFIER_LOCATION_UUID));
			identifier.getExtension().add(locationExtension);
		}
	}

	private void ensurePreferredOpenEmiIdentifier(Patient patient) {
		if (patient == null || patient.getIdentifier() == null || patient.getIdentifier().isEmpty()) {
			return;
		}

		Identifier preferredOpenEmpi = null;
		for (Identifier identifier : patient.getIdentifier()) {
			String code = identifier.hasType() && identifier.getType().hasCoding()
					? identifier.getType().getCodingFirstRep().getCode()
					: null;
			if (StringUtils.equals(code, preferredIdentifierTypeUuid)) {
				preferredOpenEmpi = identifier;
				break;
			}
		}

		if (preferredOpenEmpi == null) {
			String sourceValue = null;
			for (Identifier identifier : patient.getIdentifier()) {
				if (StringUtils.isNotBlank(identifier.getValue())) {
					sourceValue = identifier.getValue();
					break;
				}
			}
			if (StringUtils.isBlank(sourceValue)) {
				return;
			}
			preferredOpenEmpi = new Identifier();
			preferredOpenEmpi.setUse(Identifier.IdentifierUse.OFFICIAL);
			preferredOpenEmpi.setValue(sourceValue);
			preferredOpenEmpi.getType().setText(preferredIdentifierTypeName);
			preferredOpenEmpi.getType().addCoding().setCode(preferredIdentifierTypeUuid);
			patient.getIdentifier().add(preferredOpenEmpi);
		}

		boolean hasPreferredFlag = preferredOpenEmpi.getExtension().stream()
				.anyMatch(ext -> OPENMRS_IDENTIFIER_PREFERRED_EXTENSION_URL.equals(ext.getUrl()));
		if (!hasPreferredFlag) {
			Extension preferredExtension = new Extension();
			preferredExtension.setUrl(OPENMRS_IDENTIFIER_PREFERRED_EXTENSION_URL);
			preferredExtension.setValue(new BooleanType(true));
			preferredOpenEmpi.getExtension().add(preferredExtension);
		}
	}

	private boolean existsByIdentifier(Patient patient) {
		if (patient == null || patient.getIdentifier() == null || patient.getIdentifier().isEmpty()) {
			return false;
		}
		for (Identifier identifier : patient.getIdentifier()) {
			if (StringUtils.isBlank(identifier.getValue())) {
				continue;
			}
			String encoded = UriUtils.encodeQueryParam(identifier.getValue(), StandardCharsets.UTF_8.name());
			Bundle result = fhirConfig.getLocalOpenMRSFhirContext().search()
					.byUrl("Patient?identifier=" + encoded)
					.returnBundle(Bundle.class)
					.execute();
			if (result != null && result.hasEntry()) {
				return true;
			}
		}
		return false;
	}
}
