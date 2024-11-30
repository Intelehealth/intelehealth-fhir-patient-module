package org.ih.patient.data.exchange.service;


import static org.ih.patient.data.exchange.utils.DateUtils.toFormattedDateNow;

import javax.transaction.Transactional;

import org.ih.patient.data.exchange.model.IHMarker;
import org.ih.patient.data.exchange.repository.IHMarkerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class IHMarkerService {
	
	@Autowired
	private IHMarkerRepository ihRepository;
	
	public IHMarker save(IHMarker ihMarker){
		return ihRepository.save(ihMarker);
	}
	
	public IHMarker findByName(String name){
		
		IHMarker  marker= ihRepository.findByName(name);
		
		if(marker==null){
			marker = new IHMarker();
			marker.setName(name);
			marker.setLastSyncTime(toFormattedDateNow("yyyy-MM-dd HH:mm:ss"));
			save(marker);
		}
		return marker;
	}
	
	@Transactional
	public void updateMarkerByName(String name) {
		ihRepository.updateLastSyncTimeByName(name);
	}
	
	

}
