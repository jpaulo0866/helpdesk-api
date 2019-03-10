package com.viaflow.helpdesk.api.enums;

public enum StatusEnum {
	
	New,
	Assigned,
	Resolved,
	Approved,
	Disapproved,
	Closed,
	Undefined;
	
	public static StatusEnum getStatus(String status) {
		for (StatusEnum type : StatusEnum.values()) {
			if (type.name() != null
					&& type.name().equals(status)) {
				return type;
			}
		}
		return StatusEnum.Undefined;
	}
}
