package com.github.pms1.tppt.p2;

import java.io.IOException;
import java.nio.file.Path;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.codehaus.plexus.component.annotations.Component;

import com.github.pms1.tppt.p2.jaxb.artifact.ArtifactRepository;
import com.google.common.base.Throwables;

@Component(role = ArtifactRepositoryFactory.class)
public class ArtifactRepositoryFactory extends AbstractRepositoryFactory<ArtifactRepository> {

	protected ArtifactRepositoryFactory() {
		super(getJaxbContext(), ArtifactRepository.class, "artifact", "artifacts", "artifactRepository.xsd");
	}

	public ArtifactRepositoryFacade createFacade(Path p) throws IOException {
		return new ArtifactRepositoryFacadeImpl(p, readRepository(p));
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

}
