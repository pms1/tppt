package com.github.pms1.tppt.p2;

import org.codehaus.plexus.component.annotations.Component;
import org.osgi.framework.Version;

import com.github.pms1.tppt.p2.jaxb.composite.Children;
import com.github.pms1.tppt.p2.jaxb.composite.CompositeRepository;
import com.github.pms1.tppt.p2.jaxb.composite.Properties;

@Component(role = CompositeArtifactRepositoryFactory.class)
public class CompositeArtifactRepositoryFactory extends CompositeRepositoryFactory {

	protected CompositeArtifactRepositoryFactory() {
		super("compositeArtifact");
	}

	public CompositeRepository createEmpty() {
		CompositeRepository repository = new CompositeRepository();
		repository.setType("org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository");
		repository.setVersion(Version.parseVersion("1.0.0"));
		repository.setChildren(new Children());
		repository.setProperties(new Properties());
		return repository;
	}

}
