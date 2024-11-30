package org.ih.patient.data.exchange.repository;

import org.ih.patient.data.exchange.model.DataExchangeAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataExchangeAuditLogRepository  extends JpaRepository<DataExchangeAuditLog, Integer> {

}
