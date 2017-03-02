package application;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name = "mirrorSpecification")
public class MirrorSpec {
	static class Adapter extends XmlAdapter<String, Path> {

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
	@XmlJavaTypeAdapter(Adapter.class)
	public Path mirrorRepository;

	@XmlElement
	@XmlJavaTypeAdapter(Adapter.class)
	public Path targetRepository;

	@XmlElement(name = "sourceRepository")
	public URI[] sourceRepositories;

	@XmlElement(name = "iu")
	public String[] ius;

}
