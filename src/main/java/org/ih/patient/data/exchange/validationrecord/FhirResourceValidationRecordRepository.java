package org.ih.patient.data.exchange.validationrecord;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FhirResourceValidationRecordRepository
		extends JpaRepository<FhirResourceValidationRecord, Long> {
}
