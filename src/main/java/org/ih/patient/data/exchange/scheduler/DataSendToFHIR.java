package org.ih.patient.data.exchange.scheduler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.ih.patient.data.exchange.config.FhirConfig;
import org.ih.patient.data.exchange.datatype.ConfigFacilityDataType;
import org.ih.patient.data.exchange.domain.ConfigDataSync;
import org.ih.patient.data.exchange.domain.FhirResponse;
import org.ih.patient.data.exchange.domain.PatientDTO;
import org.ih.patient.data.exchange.domain.PersonAttribute;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;

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

		transferCreatedPatient();

		transferModifiedPatient();
	}

	private void transferCreatedPatient() {
		ConfigDataSync patientSync = configDataSyncService.getConfigDataSync(ConfigFacilityDataType.PATIENTS);

		if (patientSync.getStatus()) {

			IHMarker patientMarker = ihMarkerService.findByName(exportCreatedPatient);

			List<PatientDTO> patientList = patientService.getCreatedPatients(patientMarker.getLastSyncTime());

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
				ihMarkerService.updateMarkerByName(exportCreatedPatient);
			}

			System.err.println("Patient Data Sync Done............");
		} else {
			System.err.println("Patient data sending is disabled ............");
		}
	}

	private void transferModifiedPatient() {
		ConfigDataSync patientSync = configDataSyncService.getConfigDataSync(ConfigFacilityDataType.PATIENTS);

		if (patientSync.getStatus()) {

			IHMarker patientMarker = ihMarkerService.findByName(exportModifiedPatient);

			List<PatientDTO> patientList = patientService.getModifiedPatients(patientMarker.getLastSyncTime());

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
				ihMarkerService.updateMarkerByName(exportModifiedPatient);
			}

			System.err.println("Patient Data Sync Done............");
		} else {
			System.err.println("Patient data sending is disabled ............");
		}
	}

	private FhirResponse send(String resourceType, String uuid)
			throws ParseException, DataFormatException, JSONException, ConfigurationException, IOException {
		System.err.println("resourceType => " + resourceType + " => " + uuid);

		String data = HttpWebClient.get(localOpenmrsOpenhimURL, "/ws/fhir2/R4/" + resourceType + "?_id=" + uuid,
				firFhirConfig.getOpenMRSCredentials()[0], firFhirConfig.getOpenMRSCredentials()[1]);

		System.err.println("Local Fhir Bundle => " + data);

		Bundle theBundle = fhirContext.newJsonParser().parseResource(Bundle.class, data);

		return sendFHIRBundle(theBundle, resourceType);
	}

	public FhirResponse sendFHIRBundle(Bundle localTaskBundle, String resourceType)
			throws ParseException, DataFormatException, JSONException, ConfigurationException, IOException {

		String localPatientUUID = null;

		if (localTaskBundle.hasEntry()) {

			Bundle transactionBundle = new Bundle();

			Patient localPatient = null;
			transactionBundle.setType(Bundle.BundleType.TRANSACTION);
			for (BundleEntryComponent bundleEntry : localTaskBundle.getEntry()) {

				Resource resource = (Resource) bundleEntry.getResource();

				localPatient = (Patient) bundleEntry.getResource();
				localPatientUUID = localPatient.getIdElement().getIdPart();
				addExtension(localPatient, localPatientUUID);
//				if (!validateResource(localPatient)) {
//					throw new ResourceIsNotValid("Patient fhir resource is not valid");
//				}
				Bundle.BundleEntryComponent component = transactionBundle.addEntry();
				component.setResource(localPatient);

			}

			String payload = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(transactionBundle)
					.toString();

			DataExchangeAuditLog log = new DataExchangeAuditLog();
			log.setResourceName(resourceType);
			log.setResourceUuid(localPatientUUID);
			log.setRequest(payload);
			log.setRequestUrl(shrUrl + "rest/v1/patient/save");

			DataExchangeAuditLog uLog = dataExchangeService.save(log);

			FhirResponse res = HttpWebClient.postWithBasicAuth(shrUrl, "rest/v1/patient/save",
					firFhirConfig.getOpenMRSCredentials()[0], firFhirConfig.getOpenMRSCredentials()[1], payload);

			uLog.setResponse(res.getResponse());
			uLog.setResponseStatus(res.getStatusCode());
			if (res.getStatusCode().equals("200")) {
				Bundle remoteBundle = fhirContext.newJsonParser().parseResource(Bundle.class, res.getResponse());
				System.err.println("Response from central fhir: " + res.getResponse());
				syncPatientToLocal(remoteBundle, localPatient);
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

	private void syncPatientToLocal(Bundle remotePatientBundle, Patient localPatient)
			throws JsonProcessingException, UnsupportedEncodingException, JSONException, ParseException {

		if (!hasMPI(localPatient)) {
//			Identifier mpiIdentifier = getMPIIndentifierFromBundle(remotePatientBundle);
//			mpiIdentifier.setSystem(null);
//			localPatient.getIdentifier().add(mpiIdentifier);

			// Copy all the remote bundle identifier in locally,
			// to handle data loss when update operation will happend
			List<Identifier> identifiers = getIdentifiers(remotePatientBundle);

			for (Identifier identifier : identifiers) {

				// ignore local identifier copy to local patient from remote bundle
				if (matchWithLocalIdentifier(localPatient, identifier))
					continue;

				if (identifier.getType().getText().equals(globalIdentifierName)) {
					identifier.setSystem(null);
					// identifier.setSystem(centralFhirURL + "/StructureDefinition/MPI");
					localPatient.getIdentifier().add(identifier);
				} else {
					localPatient.getIdentifier().add(identifier);
				}
			}

			String payload = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(localPatient);

			System.err.println("Local patient update:  >>>>>> " + payload);

			firFhirConfig.getLocalOpenMRSFhirContext().update().resource(localPatient).execute();
			System.err.println("Local patient update with remote MPI identifier");
		} else {
			System.err.println("Local patient Already Have MPI identifier, Nothing to Update");
		}
	}

	private String makeQueryParam(Patient patient) {
		if (patient == null)
			return "";
		StringBuilder sb = new StringBuilder();

		if (patient.getBirthDate() != null) {
			String dob = new SimpleDateFormat("yyyy-MM-dd").format(patient.getBirthDate()).toString();
			sb.append("&birthdate=").append(dob);
		}

		if (patient.getGender() != null) {
			sb.append("&gender=").append(patient.getGender().toString().toLowerCase());
		}

		if (patient.getName() != null && !patient.getName().isEmpty()) {
			sb.append("&family=").append(patient.getName().get(0).getFamily());
		}

		if (patient.getName() != null && !patient.getName().isEmpty()) {
			sb.append("&given=").append(patient.getName().get(0).getGivenAsSingleString());
		}

		if (patient.getTelecom() != null && !patient.getTelecom().isEmpty()) {
			sb.append("&telecom=").append(URLEncoder.encode(patient.getTelecom().get(0).getValue()));
		}

		if (sb.length() > 0)
			return sb.substring(1);

		return sb.toString();

	}

	private String extractResourceId(Bundle bundle) {
		if (bundle.getEntry().size() != 1)
			return null;
		Resource resource = bundle.getEntryFirstRep().getResource();
		return resource.getIdElement().getIdPart();
	}

	private Identifier getMPIIndentifierFromBundle(Bundle bundle) {
		if (bundle.getEntry().size() < 1) {
			return null;
		}

		for (BundleEntryComponent bundleEntry : bundle.getEntry()) {
			Patient patient = (Patient) bundleEntry.getResource();
			for (Identifier identifier : patient.getIdentifier()) {
				if (identifier.getType().getText().equals(globalIdentifierName)) {
					return identifier;
				}
			}
		}
		return null;
	}

	private List<Identifier> getIdentifiers(Bundle bundle) {
		if (bundle.getEntry().size() < 1) {
			return new ArrayList<>();
		}

		for (BundleEntryComponent bundleEntry : bundle.getEntry()) {
			Patient patient = (Patient) bundleEntry.getResource();
			return patient.getIdentifier();

		}
		return new ArrayList<>();
	}

	private boolean hasMPI(Patient patient) {
		for (Identifier identifier : patient.getIdentifier()) {
			if (identifier.getType().getText().equals(globalIdentifierName)) {
				return true;
			}
		}
		return false;
	}

	private boolean matchWithLocalIdentifier(Patient localPatient, Identifier identifier) {

		for (Identifier localIdentifier : localPatient.getIdentifier()) {
			if (localIdentifier.getValue().equals(identifier.getValue()))
				return true;

		}
		return false;

	}

	private Patient addExtension(Patient patient, String patientUUID) {
		List<PersonAttribute> attributes = commonOperationService.findPersonAttributes(patientUUID);
		// System.err.println("Person attributes found : " + attributes.size());

		List<Extension> extensionList = new ArrayList<Extension>();
		for (PersonAttribute attribute : attributes) {
			Extension extension = new Extension();
			String url = centralFhirURL + "/StructureDefinition/" + attribute.getName().replaceAll(" ", "-");
			extension.setUrl(url);
			extension.setValue(new StringType(attribute.getValue()));
			extensionList.add(extension);
		}

		patient.getExtension().addAll(extensionList);

		for (Address address : patient.getAddress()) {
			for (Extension ext : address.getExtension()) {
				ext.setUrl(
						ext.getUrl().replace("http://fhir.openmrs.org/ext/", centralFhirURL + "/StructureDefinition/"));
				for (Extension e : ext.getExtension()) {
					e.setUrl(e.getUrl().replace("http://fhir.openmrs.org/ext/",
							centralFhirURL + "/StructureDefinition/"));
				}
			}
		}

		if (patient.getTelecom().size() > 0) {
			ContactPoint contact = patient.getTelecom().get(0);
			contact.setSystem(ContactPointSystem.PHONE);
			patient.getTelecom().set(0, contact);
		}

		return patient;
	}

	private PrePopulatedValidationSupport getCustomSupport()
			throws ConfigurationException, DataFormatException, IOException {
		FhirContext ctx = FhirContext.forR4();

		ClassPathResource extension = new ClassPathResource("structureDefinition/structureDefinition.json");

		Bundle bundle = (Bundle) ctx.newJsonParser().parseResource(new InputStreamReader(extension.getInputStream()));
		PrePopulatedValidationSupport customSupport = new PrePopulatedValidationSupport(ctx);
		// Iterate through the entries to load each StructureDefinition
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			IBaseResource resource = entry.getResource();
			if (resource instanceof StructureDefinition) {
				StructureDefinition structureDefinition = (StructureDefinition) resource;
				customSupport.addStructureDefinition(structureDefinition);
				System.err.println("Loaded StructureDefinition: " + structureDefinition.getName());
			}
		}
		return customSupport;
	}

	private boolean validateResource(Patient patient) throws ConfigurationException, DataFormatException, IOException {

		FhirValidator validator = fhirContext.newValidator();
		FhirContext ctx = FhirContext.forR4();

		// Create validation support and add the StructureDefinition
		ValidationSupportChain validationSupport = new ValidationSupportChain();
		DefaultProfileValidationSupport defaultSupport = new DefaultProfileValidationSupport(ctx);
		InMemoryTerminologyServerValidationSupport inMemSupport = new InMemoryTerminologyServerValidationSupport(ctx);

		validationSupport.addValidationSupport(inMemSupport);
		validationSupport.addValidationSupport(defaultSupport);
		validationSupport.addValidationSupport(getCustomSupport());

		FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupport);
		validator.registerValidatorModule(instanceValidator);

		ValidationResult result = validator.validateWithResult(patient);

		if (result.isSuccessful()) {
			System.out.println("Validation passed!");
		} else {
			System.err.println("Validation failed:");
			result.getMessages().forEach(msg -> {
				System.err.println(" - " + msg.getSeverity() + ": " + msg.getMessage());
			});
		}
		return result.isSuccessful();
	}

}
