package org.ih.patient.data.exchange.utils;

import org.springframework.beans.factory.annotation.Value;

public abstract class IHConstant {
	
	@Value("${resource.location.import}")
	protected String importLocation;

	@Value("${resource.location.export}")
	protected String exportLocation;

	@Value("${resource.patient_created.export}")
	protected String exportCreatedPatient;
	@Value("${resource.patient_modified.export}")
	protected String exportModifiedPatient;
	@Value("${resource.practitioner.export}")
	protected String exportPractitioner;
	@Value("${resource.encounter.export}")
	protected String exportEncounter;
	@Value("${resource.observation.export}")
	protected String exportObservation;
	@Value("${resource.medication.export}")
	protected String exportMedication;
	@Value("${resource.medication.request.export}")
	protected String exportMedicationRequest;
	@Value("${resource.service.request.export}")
	protected String exportServiceRequest;
	@Value("${resource.diagnostic.report.export}")
	protected String exportDiagnosticReport;
	@Value("${resource.identifier.name}")
	protected String globalIdentifierName;

	@Value("${local.openmrs.openhim.url}")
	public String localOpenmrsOpenhimURL;
	
	@Value("${local.openmrs.openhim.clientid.password.basic.auth}")
	protected String localOpenmrsOpenhimAuthentication;

	@Value("${opencr.openhim.url}")
	protected String opencrOpenhimURL;
	
	@Value("${opencr.openhim.clientid.password.basic.auth}")
	protected String opencrOpenhimAuthentication;

	@Value("${gofr.openhim.url}")
	protected String gofrOpenhimURL;
	@Value("${gofr.openhim.clientid.password.basic.auth}")
	protected String gofrOpenhimAuthentication;

	@Value("${opencr.shr.url}")
	protected String shrUrl;
	
	@Value("${structuredefinition.extension.url}")
	protected String sdExtensionURL;
	
	@Value("${central.fhir.url}")
	protected String centralFhirURL;

	@Value("${patient.profile.url}")
	protected String patientProfileUrl;

	@Value("${patient.profile.definition.path:structureDefinition/StructureDefinition-IH-patient-profile.json}")
	protected String patientProfileDefinitionPath;

}
