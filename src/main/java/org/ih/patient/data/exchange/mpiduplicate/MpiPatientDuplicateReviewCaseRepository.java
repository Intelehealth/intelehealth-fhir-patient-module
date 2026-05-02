package org.ih.patient.data.exchange.mpiduplicate;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MpiPatientDuplicateReviewCaseRepository extends JpaRepository<MpiPatientDuplicateReviewCase, Long> {

	Optional<MpiPatientDuplicateReviewCase> findByCaseUuid(String caseUuid);

	List<MpiPatientDuplicateReviewCase> findByLocalPatientUuidOrderByDateCreatedDesc(String localPatientUuid);

	List<MpiPatientDuplicateReviewCase> findByReviewStatusOrderByDateCreatedDesc(MpiDuplicateReviewStatus status);

	Optional<MpiPatientDuplicateReviewCase> findFirstByLocalPatientUuidAndReviewStatusOrderByIdDesc(
			String localPatientUuid, MpiDuplicateReviewStatus reviewStatus);
}
