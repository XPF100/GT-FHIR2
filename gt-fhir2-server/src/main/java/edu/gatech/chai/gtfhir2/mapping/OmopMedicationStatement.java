package edu.gatech.chai.gtfhir2.mapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.dstu3.model.Annotation;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Dosage;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.MedicationStatement;
import org.hl7.fhir.dstu3.model.MedicationStatement.MedicationStatementStatus;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.SimpleQuantity;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import edu.gatech.chai.gtfhir2.provider.EncounterResourceProvider;
import edu.gatech.chai.gtfhir2.provider.MedicationStatementResourceProvider;
import edu.gatech.chai.gtfhir2.provider.PatientResourceProvider;
import edu.gatech.chai.gtfhir2.provider.PractitionerResourceProvider;
import edu.gatech.chai.omopv5.jpa.entity.Concept;
import edu.gatech.chai.omopv5.jpa.entity.DrugExposure;
import edu.gatech.chai.omopv5.jpa.entity.FPerson;
import edu.gatech.chai.omopv5.jpa.entity.Provider;
import edu.gatech.chai.omopv5.jpa.entity.VisitOccurrence;
import edu.gatech.chai.omopv5.jpa.service.DrugExposureService;
import edu.gatech.chai.omopv5.jpa.service.ParameterWrapper;


/**
 * 
 * @author mc142
 *
 * concept id	OHDSI drug type	FHIR
 * 38000179		Physician administered drug (identified as procedure), MedicationAdministration
 * 38000180		Inpatient administration, MedicationAdministration
 * 43542356	Physician administered drug (identified from EHR problem list), MedicationAdministration
 * 43542357	Physician administered drug (identified from referral record), MedicationAdministration
 * 43542358	Physician administered drug (identified from EHR observation), MedicationAdministration
 * 581373	Physician administered drug (identified from EHR order), MedicationAdministration
 * 38000175	Prescription dispensed in pharmacy, MedicationDispense
 * 38000176	Prescription dispensed through mail order, MedicationDispense
 * 581452	Dispensed in Outpatient office, MedicationDispense
 * 38000177	Prescription written, MedicationRequest
 * ******
 * 44787730	Patient Self-Reported Medication, MedicationStatement
 * ******
 * 38000178	Medication list entry	 
 * 38000181	Drug era - 0 days persistence window	 
 * 38000182	Drug era - 30 days persistence window	 
 * 44777970	Randomized Drug	 
 */
public class OmopMedicationStatement extends BaseOmopResource<MedicationStatement, DrugExposure, DrugExposureService> implements IResourceMapping<MedicationStatement, DrugExposure> {

	private static Long MEDICATIONSTATEMENT_CONCEPT_TYPE_ID = 44787730L;
	private static OmopMedicationStatement omopMedicationStatement = new OmopMedicationStatement();
	
	public OmopMedicationStatement(WebApplicationContext context) {
		super(context, DrugExposure.class, DrugExposureService.class, MedicationStatementResourceProvider.getType());
	}

	public OmopMedicationStatement() {
		super(ContextLoaderListener.getCurrentWebApplicationContext(), DrugExposure.class, DrugExposureService.class, MedicationStatementResourceProvider.getType());
	}
	
	public static OmopMedicationStatement getInstance() {
		return omopMedicationStatement;
	}
	
	@Override
	public Long toDbase(MedicationStatement fhirResource, IdType fhirId) throws FHIRException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MedicationStatement constructFHIR(Long fhirId, DrugExposure entity) {
		MedicationStatement medicationStatement = new MedicationStatement();
		medicationStatement.setId(new IdType(fhirId));
		
		// status is required field in FHIR MedicationStatement.
		// However, we do not have a field in OMOP.
		// We will use stop_reason field to see if there is any data in there.
		// If we have data there, we set the status stopped. Otherwise, active.
		// We may need to use reasonNotTaken. But, we don't have a code for that.
		// We will use note to put the reason if exists.
		if (entity.getStopReason() != null) {
			medicationStatement.setStatus(MedicationStatementStatus.STOPPED);
			Annotation annotation = new Annotation();
			annotation.setText(entity.getStopReason());
		}
		
		FPerson fPerson = entity.getFPerson();
		if (fPerson != null) {
			Long omopFpersonId = fPerson.getId();
			Long fhirPatientId = IdMapping.getFHIRfromOMOP(omopFpersonId, MedicationStatementResourceProvider.getType());
			medicationStatement.setSubject(new Reference(new IdType(PatientResourceProvider.getType(), fhirPatientId)));
		}
		
		// See if we have encounter associated with this medication statement.
		VisitOccurrence visitOccurrence = entity.getVisitOccurrence();
		if (visitOccurrence != null) {
			Long fhirEncounterId = IdMapping.getFHIRfromOMOP(visitOccurrence.getId(), EncounterResourceProvider.getType());
			Reference reference = new Reference(new IdType(EncounterResourceProvider.getType(), fhirEncounterId));
			medicationStatement.setContext(reference);
		}
		
		// Get medicationCodeableConcept
		Concept drugConcept = entity.getDrugConcept();
		if (drugConcept != null) {
			String omopVocabularyId = drugConcept.getVocabulary().getId();
			
			// Get the mapped FHIR system uri for this.
			String fhirUri = null;
			try {
				fhirUri = OmopCodeableConceptMapping.fhirUriforOmopVocabularyi(omopVocabularyId);
			} catch (FHIRException e) {
				e.printStackTrace();
				return null;
			}
			
			if (fhirUri == null || "None".equals(fhirUri)) {
				// Failed to map the Omop Vocabulary in FHIR codeable concept.
				// For now, we return null.
				return null;
			}
			
			Coding medicationCoding = new Coding();
			medicationCoding.setSystem(fhirUri);
			medicationCoding.setCode(drugConcept.getConceptCode());
			medicationCoding.setDisplay(drugConcept.getName());
			
			CodeableConcept medication = new CodeableConcept();
			medication.addCoding(medicationCoding);
			
			medicationStatement.setMedication(medication);
		}
		
		// Get effectivePeriod
		Period period = new Period();
		Date startDate = entity.getDrugExposureStartDate();
		if (startDate != null) {
			period.setStart(startDate);
		}
		
		Date endDate = entity.getDrugExposureEndDate();
		if (endDate != null) {
			period.setEnd(endDate);
		}
		
		if (!period.isEmpty()) {
			medicationStatement.setEffective(period);
		}
		
		Double effectiveDrugDose = entity.getEffectiveDrugDose();
		Double omopQuantity = entity.getQuantity();		
		SimpleQuantity quantity = new SimpleQuantity();
		if (effectiveDrugDose != null) {
			quantity.setValue(effectiveDrugDose);			
		} else if (omopQuantity != null) {
			quantity.setValue(omopQuantity);			
		}
		
		Concept unitConcept = entity.getDoseUnitConcept();
		if (unitConcept != null) {
			try {
				String unitFhirUri = OmopCodeableConceptMapping.fhirUriforOmopVocabularyi(unitConcept.getVocabulary().getId());
				if (!"None".equals(unitFhirUri)) {
					String unitDisplay = unitConcept.getName();
					String unitCode = unitConcept.getConceptCode();
					quantity.setUnit(unitDisplay);
					quantity.setSystem(unitFhirUri);
					quantity.setCode(unitCode);
				}
			} catch (FHIRException e) {
				// We have null vocabulary id in the unit concept.
				// Throw error and move on.
				e.printStackTrace();
			}
		}

		Dosage dosage = new Dosage();
		if (!quantity.isEmpty()) {
			dosage.setDose(quantity);
		}
		
		Concept routeConcept = entity.getRouteConcept();
		if (routeConcept != null) {
			try {
				String fhirUri = OmopCodeableConceptMapping.fhirUriforOmopVocabularyi(routeConcept.getVocabulary().getId());
				if (!"None".equals(fhirUri)) {
					CodeableConcept routeCodeableConcept = new CodeableConcept();
					Coding routeCoding = new Coding();
					routeCoding.setSystem(fhirUri);
					routeCoding.setCode(routeConcept.getConceptCode());
					routeCoding.setDisplay(routeConcept.getName());
					
					routeCodeableConcept.addCoding(routeCoding);
					dosage.setRoute(routeCodeableConcept);
				}
			} catch (FHIRException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}

		String sig = entity.getSig();
		if (sig != null && !sig.isEmpty()) {
			dosage.setText(sig);
		}
		
		if (!dosage.isEmpty())
			medicationStatement.addDosage(dosage);
		
		// Get information source
		Provider provider = entity.getProvider();
		if (provider != null) {
			Long fhirPractitionerId = IdMapping.getFHIRfromOMOP(provider.getId(), PractitionerResourceProvider.getType()); 
			Reference infSourceReference = new Reference(new IdType(PractitionerResourceProvider.getType(), fhirPractitionerId));
			medicationStatement.setInformationSource(infSourceReference);
		}
		
		return medicationStatement;
	}

	@Override
	public List<ParameterWrapper> mapParameter(String parameter, Object value) {
		List<ParameterWrapper> mapList = new ArrayList<ParameterWrapper>();
		ParameterWrapper paramWrapper = new ParameterWrapper();
		switch (parameter) {
		case MedicationStatement.SP_RES_ID:
			String medicationStatementId = ((TokenParam) value).getValue();
			paramWrapper.setParameterType("Long");
			paramWrapper.setParameters(Arrays.asList("id"));
			paramWrapper.setOperators(Arrays.asList("="));
			paramWrapper.setValues(Arrays.asList(medicationStatementId));
			paramWrapper.setRelationship("or");
			mapList.add(paramWrapper);
			break;
		case MedicationStatement.SP_CODE:
			String system = ((TokenParam) value).getSystem();
			String code = ((TokenParam) value).getValue();
			
			if ((system == null || system.isEmpty()) && (code == null || code.isEmpty()))
				break;
			
			String omopVocabulary = "None";
			if (system != null && !system.isEmpty()) {
				try {
					omopVocabulary = OmopCodeableConceptMapping.omopVocabularyforFhirUri(system);
				} catch (FHIRException e) {
					e.printStackTrace();
				}
			} 

			paramWrapper.setParameterType("String");
			if ("None".equals(omopVocabulary) && code != null && !code.isEmpty()) {
				paramWrapper.setParameters(Arrays.asList("drugConcept.conceptCode"));
				paramWrapper.setOperators(Arrays.asList("like"));
				paramWrapper.setValues(Arrays.asList(code));
			} else if (!"None".equals(omopVocabulary) && (code == null || code.isEmpty())) {
				paramWrapper.setParameters(Arrays.asList("drugConcept.vocabulary.id"));
				paramWrapper.setOperators(Arrays.asList("like"));
				paramWrapper.setValues(Arrays.asList(omopVocabulary));				
			} else {
				paramWrapper.setParameters(Arrays.asList("drugConcept.vocabulary.id", "drugConcept.conceptCode"));
				paramWrapper.setOperators(Arrays.asList("like","like"));
				paramWrapper.setValues(Arrays.asList(omopVocabulary, code));
			}
			paramWrapper.setRelationship("and");
			mapList.add(paramWrapper);
			break;
		case MedicationStatement.SP_CONTEXT:
			Long fhirId = ((ReferenceParam) value).getIdPartAsLong();
			Long omopId = IdMapping.getOMOPfromFHIR(fhirId, PatientResourceProvider.getType());
			String resourceName = ((ReferenceParam) value).getResourceType();
			
			// We support Encounter so the resource type should be Encounter.
			if (EncounterResourceProvider.getType().equals(resourceName)
					&& omopId != null) {
				paramWrapper.setParameterType("Long");
				paramWrapper.setParameters(Arrays.asList("visitOccurrence.id"));
				paramWrapper.setOperators(Arrays.asList("="));
				paramWrapper.setValues(Arrays.asList(String.valueOf(omopId)));
				paramWrapper.setRelationship("or");
				mapList.add(paramWrapper);
			}
			break;
		case MedicationStatement.SP_EFFECTIVE:
			DateParam effectiveDateParam = ((DateParam) value);
			ParamPrefixEnum apiOperator = effectiveDateParam.getPrefix();
			String sqlOperator = null;
			if (apiOperator.equals(ParamPrefixEnum.GREATERTHAN)) {
				sqlOperator = ">";
			} else if (apiOperator.equals(ParamPrefixEnum.GREATERTHAN_OR_EQUALS)) {
				sqlOperator = ">=";
			} else if (apiOperator.equals(ParamPrefixEnum.LESSTHAN)) {
				sqlOperator = "<";
			} else if (apiOperator.equals(ParamPrefixEnum.LESSTHAN_OR_EQUALS)) {
				sqlOperator = "<=";
			} else if (apiOperator.equals(ParamPrefixEnum.NOT_EQUAL)) {
				sqlOperator = "!=";
			} else {
				sqlOperator = "=";
			}
			Date effectiveDate = effectiveDateParam.getValue();
			
			paramWrapper.setParameterType("Date");
			paramWrapper.setParameters(Arrays.asList("drugExposureStartDate"));
			paramWrapper.setOperators(Arrays.asList(sqlOperator));
			paramWrapper.setValues(Arrays.asList(String.valueOf(effectiveDate.getTime())));
			paramWrapper.setRelationship("or");
			mapList.add(paramWrapper);
			break;
		case MedicationStatement.SP_PATIENT:
			ReferenceParam patientReference = ((ReferenceParam) value);
			String patientId = String.valueOf(patientReference.getIdPartAsLong());
			
			paramWrapper.setParameterType("Long");
			paramWrapper.setParameters(Arrays.asList("fPerson.id"));
			paramWrapper.setOperators(Arrays.asList("="));
			paramWrapper.setValues(Arrays.asList(patientId));
			paramWrapper.setRelationship("or");
			mapList.add(paramWrapper);
			break;
		case MedicationStatement.SP_SOURCE:
			ReferenceParam sourceReference = ((ReferenceParam) value);
			String sourceReferenceId = String.valueOf(sourceReference.getIdPartAsLong());
			
			paramWrapper.setParameterType("Long");
			paramWrapper.setParameters(Arrays.asList("provider.id"));
			paramWrapper.setOperators(Arrays.asList("="));
			paramWrapper.setValues(Arrays.asList(sourceReferenceId));
			paramWrapper.setRelationship("or");
			mapList.add(paramWrapper);
			break;
		default:
			mapList = null;
		}
		
		return mapList;
	}
	
	final ParameterWrapper filterParam = new ParameterWrapper(
			"Long",
			Arrays.asList("drugTypeConcept.id"),
			Arrays.asList("="),
			Arrays.asList(String.valueOf(MEDICATIONSTATEMENT_CONCEPT_TYPE_ID)),
			"or"
			);

	@Override
	public Long getSize() {
		Map<String, List<ParameterWrapper>> map = new HashMap<String, List<ParameterWrapper>> ();
		return getSize(map);
	}

	@Override
	public Long getSize(Map<String, List<ParameterWrapper>> map) {
		List<ParameterWrapper> exceptions = new ArrayList<ParameterWrapper>();
		exceptions.add(filterParam);
		map.put(MAP_EXCEPTION_FILTER, exceptions);

		return getMyOmopService().getSize(map);
	}

	@Override
	public void searchWithoutParams(int fromIndex, int toIndex, List<IBaseResource> listResources,
			List<String> includes) {

		// This is read all. But, since we will add an exception conditions to add filter.
		// we will call the search with params method.
		Map<String, List<ParameterWrapper>> map = new HashMap<String, List<ParameterWrapper>> ();
		searchWithParams (fromIndex, toIndex, map, listResources, includes);
	}

	@Override
	public void searchWithParams(int fromIndex, int toIndex, Map<String, List<ParameterWrapper>> map,
			List<IBaseResource> listResources, List<String> includes) {
		List<ParameterWrapper> exceptions = new ArrayList<ParameterWrapper>();
		exceptions.add(filterParam);
		map.put(MAP_EXCEPTION_FILTER, exceptions);

		List<DrugExposure> entities = getMyOmopService().searchWithParams(fromIndex, toIndex, map);

		for (DrugExposure entity : entities) {
			Long omopId = entity.getIdAsLong();
			Long fhirId = IdMapping.getFHIRfromOMOP(omopId, getMyFhirResourceType());
			MedicationStatement fhirResource = constructResource(fhirId, entity, includes);
			if (fhirResource != null) {
				listResources.add(fhirResource);			
				// Do the rev_include and add the resource to the list.
				addRevIncludes(omopId, includes, listResources);
			}

		}
	}
}