package com.github.pms1.tppt.p2;

import org.codehaus.plexus.component.annotations.Component;
import org.osgi.framework.Version;

import com.github.pms1.tppt.p2.jaxb.composite.Children;
import com.github.pms1.tppt.p2.jaxb.composite.CompositeRepository;
import com.github.pms1.tppt.p2.jaxb.composite.CompositeProperties;

@Component(role = CompositeMetadataRepositoryFactory.class)
public class CompositeMetadataRepositoryFactory extends CompositeRepositoryFactory {

	protected CompositeMetadataRepositoryFactory() {
		super("compositeMetadata");
	}

	public CompositeRepository createEmpty() {
		CompositeRepository repository = new CompositeRepository();
		repository.setType("org.eclipse.equinox.internal.p2.artifact.repository.CompositeMetadataRepository");
		repository.setVersion(Version.parseVersion("1.0.0"));
		repository.setChildren(new Children());
		repository.setProperties(new CompositeProperties());
		return repository;
	}

}
