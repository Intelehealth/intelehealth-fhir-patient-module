package org.ih.patient.data.exchange.scheduler;

import static org.ih.patient.data.exchange.utils.DateUtils.toFormattedDateNow;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.ih.patient.data.exchange.config.FhirConfig;
import org.ih.patient.data.exchange.datatype.ConfigFacilityDataType;
import org.ih.patient.data.exchange.domain.ConfigDataSync;
import org.ih.patient.data.exchange.domain.FhirResponse;
import org.ih.patient.data.exchange.domain.PatientDTO;
import org.ih.patient.data.exchange.model.DataExchangeAuditLog;
import org.ih.patient.data.exchange.model.IHMarker;
import org.ih.patient.data.exchange.service.CommonOperationService;
import org.ih.patient.data.exchange.service.ConfigDataSyncService;
import org.ih.patient.data.exchange.service.DataExchangeAuditLogService;
import org.ih.patient.data.exchange.service.IHMarkerService;
import org.ih.patient.data.exchange.service.PatientDataService;
import org.ih.patient.data.exchange.service.VisitTypeService;
import org.ih.patient.data.exchange.utils.DateUtils;
import org.ih.patient.data.exchange.utils.HttpWebClient;
import org.ih.patient.data.exchange.utils.IHConstant;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.api.MethodOutcome;

@Component
public class DataSendToFHIR extends IHConstant {

	FhirContext fhirContext = FhirContext.forR4();

	@Autowired
	private FhirConfig firFhirConfig;

	@Autowired
	private VisitTypeService visitType;

	@Autowired
	private IHMarkerService ihMarkerService;

	@Autowired
	private CommonOperationService commonOperationService;

	@Autowired
	private ConfigDataSyncService configDataSyncService;

	@Autowired
	private PatientDataService patientService;

	@Autowired
	private DataExchangeAuditLogService dataExchangeService;

	@Scheduled(fixedDelay = 60000, initialDelay = 60000)
	public void scheduleTaskUsingCronExpression() throws ParseException, UnsupportedEncodingException,
			DataFormatException, JsonProcessingException, JSONException {

		ConfigDataSync patientSync = configDataSyncService.getConfigDataSync(ConfigFacilityDataType.PATIENTS);

		if (patientSync.getStatus()) {

			IHMarker patientMarker = ihMarkerService.findByName(exportPatient);

			List<PatientDTO> patientList = patientService.getPatients(patientMarker.getLastSyncTime());

			HashSet<String> patientIdList = new HashSet<>(
					patientList.stream().map(p -> p.getUuid()).collect(Collectors.toSet()));

			System.err.println("Total Patient Found: " + patientIdList.size());

			int patientSendingError = 0;

			for (String patient : patientIdList) {
				try {
					send("Patient", patient);
				} catch (Exception e) {
					System.err.println(e);
					patientSendingError++;
				}
			}

			System.err.format("Total patient found: %d, Successfully Send %d, Error %d\n", patientIdList.size(),
					patientIdList.size() - patientSendingError, patientSendingError);

			if (patientIdList.size() > 0) {
				ihMarkerService.updateMarkerByName(exportPatient);
			}

			System.err.println("Patient Data Sync Done............");
		} else {
			System.err.println("Patient data sending is disabled ............");
		}
	}

	private FhirResponse send(String resourceType, String uuid) throws ParseException, UnsupportedEncodingException,
			DataFormatException, JsonProcessingException, JSONException {
		System.err.println("resourceType => " + resourceType + " => " + uuid);

		String data = HttpWebClient.get(localOpenmrsOpenhimURL, "/ws/fhir2/R4/" + resourceType + "?_id=" + uuid,
				firFhirConfig.getOpenMRSCredentials()[0], firFhirConfig.getOpenMRSCredentials()[1]);

		System.err.println("Local Fhir Bundle => " + data);

		Bundle theBundle = fhirContext.newJsonParser().parseResource(Bundle.class, data);

		return sendFHIRBundle(theBundle, resourceType);
	}

	public FhirResponse sendFHIRBundle(Bundle localTaskBundle, String resourceType) throws ParseException,
			UnsupportedEncodingException, DataFormatException, JsonProcessingException, JSONException {

		String localPatientUUID = "";

		if (localTaskBundle.hasEntry()) {

			Bundle transactionBundle = new Bundle();

			Patient patient = null;
			transactionBundle.setType(Bundle.BundleType.TRANSACTION);
			for (BundleEntryComponent bundleEntry : localTaskBundle.getEntry()) {

				Resource resource = (Resource) bundleEntry.getResource();
				patient = (Patient) bundleEntry.getResource();
				localPatientUUID = patient.getIdElement().getIdPart();

				Bundle.BundleEntryComponent component = transactionBundle.addEntry();
				component.setResource(resource);
				component.getRequest().setUrl(resource.fhirType() + "/" + resource.getIdElement().getIdPart())
						.setMethod(Bundle.HTTPVerb.PUT);

			}

			String payload = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(transactionBundle)
					.toString();

			DataExchangeAuditLog log = new DataExchangeAuditLog();
			log.setResourceName(resourceType);
			log.setResourceUuid(localPatientUUID);
			log.setRequest(payload);
			log.setRequestUrl(shrUrl + "rest/v1/bundle/save");

			DataExchangeAuditLog uLog = dataExchangeService.save(log);

			FhirResponse res = HttpWebClient.postWithBasicAuth(shrUrl, "rest/v1/bundle/save",
					firFhirConfig.getOpenMRSCredentials()[0], firFhirConfig.getOpenMRSCredentials()[1], payload);

			uLog.setResponse(res.getResponse());
			uLog.setResponseStatus(res.getStatusCode());
			if (res.getStatusCode().equals("200")) {
				Bundle remoteBundle = fhirContext.newJsonParser().parseResource(Bundle.class, res.getResponse());
				System.err.println("Response from central fhir: " + res.getResponse());
				savePatientToLocal(remoteBundle, localPatientUUID, "");
				uLog.setFhirId(extractResourceId(remoteBundle));
			} else {
				uLog.setStatus(false);
			}
			uLog.setChangedBy(1); // Admin-OpenMRS
			uLog.setDateChanged(DateUtils.toFormattedDateNow());
			dataExchangeService.update(uLog);
			return res;
		}
		return null;
	}

	private Bundle savePatientToLocal(Bundle remotePatientBundle, String localPatientUUID, String locationUuid)
			throws JsonProcessingException, UnsupportedEncodingException, JSONException, ParseException {

		Bundle transactionBundle = new Bundle();

		transactionBundle.setType(Bundle.BundleType.TRANSACTION);

		for (BundleEntryComponent bundleEntry : remotePatientBundle.getEntry()) {

			Patient patient = (Patient) bundleEntry.getResource();

			Resource resource = (Resource) bundleEntry.getResource();

			String patientUUID = resource.getIdElement().getIdPart();

			System.err.println("PatientID ===> " + patientUUID);

			int i = 0;

			// handle multiple identifier
			boolean fhirIdentifierNotLinked = true;

			for (Identifier identifier : patient.getIdentifier()) {

				String code = identifier.getType().getCodingFirstRep().getCode();

				String identifierUUid = commonOperationService.findResourceUuidByName("patient_identifier_type",
						identifier.getType().getText(), "name", "uuid");

				if (StringUtils.isBlank(identifierUUid)) {
					identifierUUid = visitType.saveVisitType(identifier.getType().getText(), code,
							"patientidentifiertype");
				}

				identifier.getType().getCoding().get(0).setCode(identifierUUid);
				identifier.getType().setText(identifier.getType().getText());
				identifier.setSystem("");

				Extension ex = patient.getIdentifier().get(i).getExtensionFirstRep();

				Reference providerReference = (Reference) ex.getValue();

				if (StringUtils.isBlank(locationUuid)) {
					String location = providerReference.getReference().split("/")[1];
					String locationName = providerReference.getDisplay();

					String locUUID = commonOperationService.findResourceUuidByName("location", locationName, "name",
							"uuid");
					if (!StringUtils.isBlank(locUUID)) {
						locationUuid = locUUID;
					} else {
						importLocation(location);
					}

				} else {
					providerReference.setReference("Location/" + locationUuid);
				}

				i++;

				if (identifier.getValue().equals(patientUUID)
						|| identifier.getType().getText().equals(globalIdentifierName)) {
					fhirIdentifierNotLinked = false;
					System.err.println("Patient already linked with fhir (id) (MPI) identifier : " + patientUUID);
				}
			}

			String centralFhirIdentifierType = globalIdentifierName; // TODO MPI ID (Create in OpenMRS before
																		// synchronization)

			String coding = commonOperationService.findResourceUuidByName("patient_identifier_type",
					centralFhirIdentifierType, "name", "uuid");

//			 Saving central @Fhir resource id as identifier in local openmrs database
			Identifier identifier = patient.getIdentifier().get(0).copy();

			if (fhirIdentifierNotLinked) {
				identifier.setId(UUID.randomUUID().toString());
				identifier.setValue(patientUUID);
				identifier.getType().setText(centralFhirIdentifierType);
				identifier.getType().getCoding().get(0).setCode(coding);
				patient.getIdentifier().add(identifier);

				// Remote fhir object resource id replacing with local resource id before save
				// to locally
				patient.getIdElement().setId(localPatientUUID);
			}

			System.err.println("Modified Patient >>>>>>>>\n"
					+ fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(patient));

			firFhirConfig.getLocalOpenMRSFhirContext().update().resource(patient).execute();
			System.out.println("Updating Patient Resource .............");

		}

		return transactionBundle;
	}

//	private String makeQueryParam(Patient patient) {
//		if (patient == null)
//			return "";
//		StringBuilder sb = new StringBuilder();
//		if (patient.getBirthDate() != null) {
//			String dob = new SimpleDateFormat("yyyy-MM-dd").format(patient.getBirthDate()).toString();
//			sb.append("&birthdate=").append(dob);
//		}
//		if (patient.getGender() != null) {
//			sb.append("&gender=").append(patient.getGender().toString().toLowerCase());
//		}
//		if (patient.getIdentifier() != null && !patient.getIdentifier().isEmpty()) {
//			sb.append("&identifier=").append(patient.getIdentifier().get(0).getValue());
//		}
//		if (patient.getName() != null && !patient.getName().isEmpty()) {
//			sb.append("&family=").append(patient.getName().get(0).getFamily());
//		}
//		if (patient.getName() != null && !patient.getName().isEmpty()) {
//			sb.append("&given=").append(patient.getName().get(0).getGivenAsSingleString());
//		}
//
//		if (sb.length() > 0)
//			return sb.substring(1);
//
//		return sb.toString();
//
//	}

	public void importLocation(String locationId)
			throws JsonProcessingException, UnsupportedEncodingException, JSONException, ParseException {

		String data = HttpWebClient.get(shrUrl, "rest/v1/bundle/Location?_id=" + locationId,
				firFhirConfig.getOpenMRSCredentials()[0], firFhirConfig.getOpenMRSCredentials()[1]);
		Bundle theBundle = fhirContext.newJsonParser().parseResource(Bundle.class, data);
		System.err.println("locationId::" + locationId);
		saveLocationToLocal(theBundle);

	}

	private Bundle saveLocationToLocal(Bundle originalTasksBundle) {
		Bundle transactionBundle = new Bundle();

		transactionBundle.setType(Bundle.BundleType.TRANSACTION);
		for (BundleEntryComponent bundleEntry : originalTasksBundle.getEntry()) {
			Location r = (Location) bundleEntry.getResource();

			Resource resource = (Resource) bundleEntry.getResource();

			Integer id = commonOperationService.findLoationByUuid("location", resource.getIdElement().getIdPart(),
					r.getName());

			System.err.println("IDL::::" + id);

			if (r.getName() != null) {
				if (id == null || id == 0) {
					MethodOutcome res = firFhirConfig.getLocalOpenMRSFhirContext().create().resource(r).execute();
					id = commonOperationService.findLoationByUuid("location", res.getId().getIdPart(), r.getName());
					commonOperationService.updateResource("location", id, resource.getIdElement().getIdPart(),
							"location_id");
				} else {
					firFhirConfig.getLocalOpenMRSFhirContext().update().resource(r).execute();
				}
			}
		}
		return transactionBundle;
	}

	private String extractResourceId(Bundle bundle) {
		if (bundle.getEntry().size() != 1)
			return null;
		Resource resource = bundle.getEntryFirstRep().getResource();
		return resource.getIdElement().getIdPart();
	}

}
