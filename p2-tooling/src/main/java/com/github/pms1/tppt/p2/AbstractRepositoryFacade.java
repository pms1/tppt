package com.github.pms1.tppt.p2;

import java.util.List;
import java.util.Optional;

import com.github.pms1.tppt.p2.jaxb.Properties;
import com.github.pms1.tppt.p2.jaxb.Property;
import com.github.pms1.tppt.p2.jaxb.Repository;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

abstract class AbstractRepositoryFacade<T extends Repository> implements RepositoryFacade<T> {

	private final Supplier<Property> createProperty;

	private final static String P2_TIMESTAMP = "p2.timestamp";

	AbstractRepositoryFacade(Supplier<Property> createProperty) {
		this.createProperty = createProperty;
	}

	@SuppressWarnings("unchecked")
	private <P> void set(List<P> l, Object l1) {
		l.add((P) l1);
	}

	@Override
	public void setTimestamp(long time) {
		Preconditions.checkArgument(time > 0);

		Repository r = getRepository();

		Properties properties = r.getProperties();

		List<? extends Property> property = properties.getProperty();

		Optional<? extends Property> prop = property.stream().filter(p -> p.getName().equals(P2_TIMESTAMP)).findAny();

		Property prop2;
		if (!prop.isPresent()) {
			prop2 = createProperty.get();
			prop2.setName(P2_TIMESTAMP);
			set(property, prop2);
		} else {
			prop2 = prop.get();
		}
		prop2.setValue(String.valueOf(time));
	}

	@Override
	public Long getTimestamp() {
		Repository r = getRepository();
		Properties properties = r.getProperties();

		List<? extends Property> property = properties.getProperty();

		Optional<? extends Property> prop = property.stream().filter(p -> p.getName().equals(P2_TIMESTAMP)).findAny();

		if (prop.isPresent())
			return Long.valueOf(prop.get().getValue());
		else
			return null;
	}

}
