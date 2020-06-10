package com.github.pms1.tppt.core;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Specification of a repository for deployment and baselining.
 * 
 * Can be directly uses as type of a property in a mojo.
 * 
 * @author pms1
 *
 */
public class DeploymentRepository {
	private Pattern pattern = Pattern.compile("(?<id>.+)::(?<uri>.+)");

	public DeploymentRepository() {
	}

	public void set(String text) {
		Matcher m = pattern.matcher(text);
		if (!m.matches())
			throw new IllegalArgumentException("Deployment repository must have the form '<serverId>::<uri>'");

		String serverId = m.group("id");
		URI uri = URI.create(m.group("uri"));

		this.serverId = serverId;
		this.uri = uri;
	}

	public String serverId;
	public URI uri;

}
