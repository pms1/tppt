package com.github.pms1.tppt.p2;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.codehaus.plexus.component.annotations.Component;

import com.github.pms1.tppt.p2.jaxb.artifact.ArtifactRepository;
import com.github.pms1.tppt.p2.jaxb.artifact.Artifacts;
import com.github.pms1.tppt.p2.jaxb.artifact.Mappings;
import com.github.pms1.tppt.p2.jaxb.artifact.ArtifactProperties;
import com.google.common.base.Throwables;

@Component(role = ArtifactRepositoryFactory.class)
public class ArtifactRepositoryFactory extends AbstractRepositoryFactory<ArtifactRepository> {

	protected ArtifactRepositoryFactory() {
		super(getJaxbContext(), ArtifactRepository.class, "artifact", "1.1.0", "artifactRepository.xsd");
	}

	private static class Holder {
		private final static JAXBContext context;
		static {
			try {
				context = JAXBContext.newInstance(ArtifactRepository.class);
			} catch (JAXBException t) {
				throw Throwables.propagate(t);
			}
		}
	}

	public static JAXBContext getJaxbContext() {
		return Holder.context;
	}

	@Override
	protected void normalize(ArtifactRepository t) {
		ArtifactProperties properties = t.getProperties();
		if (properties != null)
			properties.setSize(properties.getProperty().size());

		Artifacts artifacts = t.getArtifacts();
		if (artifacts != null)
			artifacts.setSize(artifacts.getArtifact().size());

		Mappings mappings = t.getMappings();
		if (mappings != null)
			mappings.setSize(mappings.getRule().size());
	}

	@Override
	protected ArtifactRepository createEmpty() {
		throw new UnsupportedOperationException();
	}

}
