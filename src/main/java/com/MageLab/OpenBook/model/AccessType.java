package com.MageLab.OpenBook.model;

public enum AccessType {
	FREE("Gratuito"),
	PAID("Pago"),
	UNKNOWN("A verificar");

	private final String label;

	AccessType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
