package org.ih.patient.data.exchange.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.springframework.stereotype.Service;

@Service
@Entity
@Table(name = "ih_marker", schema = "public")
public class IHMarker {
	
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Integer id;
	
	private String name;
	
	private String lastSyncTime;
	
	
	public Integer getId() {
		return id;
	}
	
	
	public void setId(Integer id) {
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getLastSyncTime() {
		return lastSyncTime;
	}
	
	public void setLastSyncTime(String lastSyncTime) {
		this.lastSyncTime = lastSyncTime;
	}


	@Override
	public String toString() {
		return "IHMarker [id=" + id + ", name=" + name + ", lastSyncTime="
				+ lastSyncTime + ", getId()=" + getId() + ", getName()="
				+ getName() + ", getLastSyncTime()=" + getLastSyncTime()
				+ ", getClass()=" + getClass() + ", hashCode()=" + hashCode()
				+ ", toString()=" + super.toString() + "]";
	}
}
