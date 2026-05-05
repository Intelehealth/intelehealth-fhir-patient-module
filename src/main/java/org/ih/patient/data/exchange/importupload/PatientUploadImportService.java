package org.ih.patient.data.exchange.importupload;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.ih.patient.data.exchange.config.FhirConfig;
import org.ih.patient.data.exchange.param.ReuestParam;
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

	public PatientUploadImportResponse importPatientFile(MultipartFile file, String locationUuid) throws Exception {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("File is empty");
		}

		String content = new String(file.getBytes(), StandardCharsets.UTF_8);
		List<Patient> patients = parsePatients(content);
		String effectiveLocationUuid = resolveIdentifierLocationUuid(locationUuid);

		PatientUploadImportResponse response = new PatientUploadImportResponse();
		response.setTotal(patients.size());

		for (Patient patient : patients) {
			PatientUploadImportItemResult item = new PatientUploadImportItemResult();
			item.setInputId(patient.getIdElement() != null ? patient.getIdElement().getIdPart() : null);
			try {
				ensurePreferredOpenEmiIdentifier(patient);
				ensureIdentifierLocation(patient, effectiveLocationUuid);
				if (existsByIdentifier(patient)) {
					item.setStatus("SKIPPED");
					item.setMessage("Patient already exists by identifier");
					response.setSkipped(response.getSkipped() + 1);
				} else if (existsByDemographics(patient)) {
					item.setStatus("SKIPPED");
					item.setMessage("Patient already exists (same family, given, gender, telecom, birth date)");
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

	private String resolveIdentifierLocationUuid(String locationUuid) {
		if (StringUtils.isNotBlank(locationUuid)) {
			return locationUuid.trim();
		}
		return OPENMRS_DEFAULT_IDENTIFIER_LOCATION_UUID;
	}

	private void ensureIdentifierLocation(Patient patient, String locationUuidForExtension) {
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
			locationExtension.setValue(new Reference("Location/" + locationUuidForExtension));
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

	/**
	 * When family, given, gender, telecom, and birth date are all present, searches the local OpenMRS
	 * FHIR store and skips create if a patient matches all of those fields. Does not alter
	 * {@link #existsByIdentifier(Patient)} behavior.
	 * <p>
	 * The server query uses only {@code birthdate}, {@code family}, and {@code _count}: OpenMRS FHIR2
	 * returns HTTP 400 for compound searches that include {@code telecom} (and similar multi-param
	 * combinations). Given, gender, and telecom are matched only in {@link #demographicsEqual}.
	 */
	private boolean existsByDemographics(Patient patient) {
		if (!hasAllDemographicsForDuplicateCheck(patient)) {
			return false;
		}
		Map<String, String> query = buildLocalDemographicSearchMap(patient);
		if (query.isEmpty()) {
			return false;
		}
		String searchParamString = ReuestParam.toQueryParam(query);
		Bundle result = fhirConfig.getLocalOpenMRSFhirContext().search()
				.byUrl("Patient?" + searchParamString)
				.returnBundle(Bundle.class)
				.execute();
		if (result == null || !result.hasEntry()) {
			return false;
		}
		for (Bundle.BundleEntryComponent entry : result.getEntry()) {
			if (entry.getResource() instanceof Patient) {
				if (demographicsEqual(patient, (Patient) entry.getResource())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasAllDemographicsForDuplicateCheck(Patient patient) {
		if (patient == null || !patient.hasBirthDate() || !patient.hasGender() || !patient.hasName()) {
			return false;
		}
		HumanName name = patient.getNameFirstRep();
		if (name == null || StringUtils.isBlank(name.getFamily())) {
			return false;
		}
		if (StringUtils.isBlank(name.getGivenAsSingleString())) {
			return false;
		}
		if (!patient.hasTelecom() || patient.getTelecom().isEmpty()
				|| StringUtils.isBlank(patient.getTelecom().get(0).getValue())) {
			return false;
		}
		return true;
	}

	private static Map<String, String> buildLocalDemographicSearchMap(Patient patient) {
		Map<String, String> m = new LinkedHashMap<>();
		HumanName name = patient.getNameFirstRep();
		m.put("birthdate", new SimpleDateFormat("yyyy-MM-dd").format(patient.getBirthDate()));
		m.put("family", name.getFamily().trim());
		m.put("_count", "50");
		return m;
	}

	private static boolean demographicsEqual(Patient incoming, Patient found) {
		if (!hasAllDemographicsForDuplicateCheck(incoming) || found == null || !found.hasBirthDate()
				|| !found.hasGender() || !found.hasName()) {
			return false;
		}
		HumanName inName = incoming.getNameFirstRep();
		HumanName fnName = found.getNameFirstRep();
		if (!StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(inName.getFamily()),
				StringUtils.trimToEmpty(fnName.getFamily()))) {
			return false;
		}
		if (!StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(inName.getGivenAsSingleString()),
				StringUtils.trimToEmpty(fnName.getGivenAsSingleString()))) {
			return false;
		}
		if (incoming.getGender() != found.getGender()) {
			return false;
		}
		String inDob = new SimpleDateFormat("yyyy-MM-dd").format(incoming.getBirthDate());
		String fdDob = new SimpleDateFormat("yyyy-MM-dd").format(found.getBirthDate());
		if (!StringUtils.equals(inDob, fdDob)) {
			return false;
		}
		String inTel = normalizeTelecom(incoming.getTelecom().get(0).getValue());
		if (found.hasTelecom()) {
			for (ContactPoint cp : found.getTelecom()) {
				if (cp.getValue() != null && inTel.equals(normalizeTelecom(cp.getValue()))) {
					return true;
				}
			}
		}
		return false;
	}

	private static String normalizeTelecom(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replaceAll("\\D", "");
	}
}
