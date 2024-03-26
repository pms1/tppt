package com.github.pms1.tppt.mirror;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

import com.github.pms1.tppt.mirror.jaxb.Proxy;

public class MirrorSpec implements Serializable {
	public static class AuthenticatedUri implements Serializable {
		public URI uri;

		public String username;

		public String password;
	}

	public static class SourceRepository implements Serializable {
		public URI uri;

		public String updatePolicy;
	}

	public URI mirrorRepository;

	public URI targetRepository;

	public SourceRepository[] sourceRepositories;

	public AuthenticatedUri[] servers;

	public Map<URI, AuthenticatedUri> mirrors;

	public String[] ius;

	public String[] excludeIus;

	public AlgorithmType algorithm;

	public static enum AlgorithmType {
		slicer, permissiveSlicer, planner;
	}

	public OfflineType offline;

	public static enum OfflineType {
		offline, online;
	}

	public StatsType stats;

	public static enum StatsType {
		collect, suppress;
	}

	public Map<String, String>[] filters;

	public Proxy proxy;
}
