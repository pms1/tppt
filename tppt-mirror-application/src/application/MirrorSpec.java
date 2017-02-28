package application;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "mirrorSpecification")
public class MirrorSpec {
	@XmlElement
	public String[] ius;
}
