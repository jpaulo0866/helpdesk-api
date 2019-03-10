package com.viaflow.helpdesk.api.security.jwt;

import java.io.Serializable;

public class JwtAuthenticationRequest implements Serializable {

	private static final long serialVersionUID = -7756483032522422105L;
	
	private String email;
	private String password;
	
	
	public JwtAuthenticationRequest() {
		super();
		// TODO Auto-generated constructor stub
	}


	public JwtAuthenticationRequest(String email, String password) {
		super();
		this.email = email;
		this.password = password;
	}


	public String getEmail() {
		return email;
	}


	public void setEmail(String email) {
		this.email = email;
	}


	public String getPassword() {
		return password;
	}


	public void setPassword(String password) {
		this.password = password;
	}
	
	
	
}
