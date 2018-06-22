package com.github.pms1.tppt.mirror;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name = "mirrorSpecification")
public class MirrorSpec {
	static class PathAdapter extends XmlAdapter<String, Path> {

		@Override
		public Path unmarshal(String v) throws Exception {
			return Paths.get(v);
		}

		@Override
		public String marshal(Path v) throws Exception {
			return v.toString();
		}

	}

	@XmlElement
	@XmlJavaTypeAdapter(PathAdapter.class)
	public Path mirrorRepository;

	@XmlElement
	@XmlJavaTypeAdapter(PathAdapter.class)
	public Path targetRepository;

	@XmlElement(name = "sourceRepository")
	public URI[] sourceRepositories;

	@XmlElement
	public Map<URI, URI> mirrors;

	@XmlElement(name = "iu")
	public String[] ius;

	@XmlElement(name = "excludeIu")
	public String[] excludeIus;

	@XmlElement
	public AlgorithmType algorithm;

	public static enum AlgorithmType {
		slicer, permissiveSlicer, planner;
	}

	@XmlElement
	public OfflineType offline;

	public static enum OfflineType {
		offline, online;
	}

	@XmlElement
	public StatsType stats;

	public static enum StatsType {
		collect, suppress;
	}

	public static class Filter {
		@XmlElement
		Map<String, String> filter;
	}

	static class MapAdapter extends XmlAdapter<MapAdapter.Entry[], Map<String, String>> {

		public static class Entry {
			public String key;
			public String value;
		}

		private Entry from(Map.Entry<String, String> e) {
			Entry r = new Entry();
			r.key = e.getKey();
			r.value = e.getValue();
			return r;
		}

		@Override
		public MapAdapter.Entry[] marshal(Map<String, String> v) throws Exception {
			return v.entrySet().stream().map(this::from).toArray(s -> new MapAdapter.Entry[s]);
		}

		@Override
		public Map<String, String> unmarshal(MapAdapter.Entry[] v) throws Exception {
			return Arrays.stream(v).collect(Collectors.toMap(e -> e.key, e -> e.value));
		}

	}

	@XmlElement(name = "filter")
	@XmlJavaTypeAdapter(MapAdapter.class)
	public Map<String, String>[] filters;
}
