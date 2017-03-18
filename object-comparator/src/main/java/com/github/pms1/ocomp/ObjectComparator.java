package com.github.pms1.ocomp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class ObjectComparator<T> {
	private final LinkedHashMap<DecomposerMatcher, DecomposerFactory> locationDecomposers;

	private final LinkedHashMap<Function<Type, Boolean>, BiFunction<Object, Object, Boolean>> comparators;

	public static class OPath2 {

		private final LinkedList<OPath2> parents;
		private final String pathx;
		private final Object left;
		private final Object right;

		public OPath2(OPath2 parent, String path, Object left, Object right) {

			if (parent == null)
				this.parents = new LinkedList<>();
			else {
				this.parents = new LinkedList<>(parent.parents);
				this.parents.add(parent);
			}
			this.pathx = path;
			this.left = left;
			this.right = right;
		}

		static OPath2 root(Object left, Object right) {
			return new OPath2(null, "/", left, right);
		}

		OPath2 child(String path, Object left, Object right) {
			Objects.requireNonNull(path);
			return new OPath2(this, path, left, right);
		}

		public String getPath() {
			StringBuilder b = new StringBuilder();
			for (OPath2 o : parents)
				b.append(o.pathx);
			b.append(pathx);
			return b.toString();
		}

		public Object getLeft() {
			return left;
		}

		public Object getRight() {
			return right;
		}

		public OPath2 getParent() {
			if (parents.isEmpty())
				return null;
			else
				return parents.getLast();
		}

		public OPath2 subPath(int begin, int end) {
			if (begin == 0) {
				if (end > parents.size() + 1)
					throw new IllegalArgumentException();
				else if (end == parents.size() + 1)
					return this;
				else
					return parents.get(end - 1);
			} else {
				return subPath(begin).subPath(0, end - begin);
			}
		}

		public OPath2 subPath(int begin) {
			if (begin == parents.size() + 1)
				return null;

			Iterator<OPath2> p = parents.iterator();
			for (int i = begin; i-- > 0;)
				p.next();

			OPath2 last = null;
			while (p.hasNext()) {
				OPath2 n = p.next();
				last = new OPath2(last, n.pathx, n.left, n.right);
			}
			return new OPath2(last, pathx, left, right);
		}

		@Override
		public String toString() {
			return "OPath2(" + getPath() + ")";
		}

		public int size() {
			return parents.size() + 1;
		}
	};

	public static class OPath {
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((path == null) ? 0 : path.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			OPath other = (OPath) obj;
			if (path == null) {
				if (other.path != null)
					return false;
			} else if (!path.equals(other.path))
				return false;
			return true;
		}

		private String path;

		public OPath(String path) {
			this.path = path;
		}

		private static final OPath ROOT = new OPath("/");

		public static OPath root() {
			return ROOT;
		}

		@Override
		public String toString() {
			return path;
		}

		public static OPath content(String name) {
			return new OPath("/" + name);
		}

		public static OPath index(String condition) {
			return new OPath("[" + condition + "]");
		}

		public OPath append(OPath key) {
			return new OPath(path + key.path);
		}

	}

	public interface DecomposerFactory {
		<T> Decomposer<T> generate(Type t);
	}

	interface Comparator {
		boolean compare(OPath2 p, Object o1, Object o2);
	}

	interface CompararerFactory {
		Comparator generate(ObjectComparator<?> oc, Type t);
	}

	private List<DecomposerFactory> decomposerFactories = Arrays.asList(list, jaxb);

	private List<CompararerFactory> factories = Arrays.asList(classIdentity, primitiveTypes, naturalEquals);

	static DecomposerFactory jaxb = new DecomposerFactory() {
		@Override
		public <T> Decomposer<T> generate(Type t) {
			if (!(t instanceof Class))
				return null;
			Class<?> c = (Class<?>) t;

			XmlAccessorType annotation = c.getAnnotation(XmlAccessorType.class);
			if (annotation == null || annotation.value() != XmlAccessType.FIELD)
				return null;

			if (c.getSuperclass() != Object.class)
				throw new UnsupportedOperationException();

			Field[] fields = c.getDeclaredFields();
			for (Field f : fields)
				f.setAccessible(true);

			return (o) -> {
				DecomposedObject result = new DecomposedObject();
				for (Field f : fields) {
					try {
						result.put(OPath.content(f.getName()), f.getGenericType(), f.get(o));
					} catch (ReflectiveOperationException e) {
						throw new RuntimeException(e);
					}
				}
				return result;
			};
		}
	};

	static DecomposerFactory list = new DecomposerFactory() {

		@Override
		public <T> Decomposer<T> generate(Type t) {
			if (!List.class.isAssignableFrom(asClass(t)))
				return null;
			// if (!(t instanceof ParameterizedType))
			// return null;
			//
			// ParameterizedType pt = (ParameterizedType) t;
			// System.err.println("PT " + pt);
			// if (!List.class.isAssignableFrom((Class<?>) pt.getRawType()))
			// return null;
			// System.err.println("PT2 " + pt);
			// if (pt.getActualTypeArguments().length != 1)
			// throw new IllegalStateException();
			// System.err.println("PT3 " + pt);
			// Type et = pt.getActualTypeArguments()[0];

			return (o) -> {
				DecomposedObject result = new DecomposedObject();
				int i = 0;
				for (Object e : (List<?>) o)
					result.put(OPath.index("" + i++), e);
				return result;
			};
		}
	};

	ObjectComparator(DeltaCreator<T> deltaCreator,
			LinkedHashMap<Function<Type, Boolean>, BiFunction<Object, Object, Boolean>> comparators,
			LinkedHashMap<DecomposerMatcher, DecomposerFactory> locationDecomposers) {
		this.deltaCreator = deltaCreator;
		this.comparators = comparators;
		this.locationDecomposers = locationDecomposers;
	}

	static Class<?> asClass(Type t) {
		if (t instanceof ParameterizedType) {
			return (Class<?>) ((ParameterizedType) t).getRawType();
		} else if (t instanceof Class) {
			return (Class<?>) t;
		} else {
			throw new Error();
		}
	}

	static CompararerFactory primitiveTypes = new CompararerFactory() {
		@Override
		public Comparator generate(ObjectComparator<?> oc, Type t) {
			if (!(t instanceof Class))
				return null;
			Class<?> c = (Class<?>) t;
			if (!c.isPrimitive())
				return null;
			return (p, o1, o2) -> {
				return o1.equals(o2);
			};
		}
	};

	static CompararerFactory classIdentity = new CompararerFactory() {
		@Override
		public Comparator generate(ObjectComparator<?> oc, Type t) {
			if (asClass(t) != Class.class)
				return null;

			return (p, o1, o2) -> {
				return o1 == o2;
			};
		}
	};

	// static CompararerFactory list = new CompararerFactory() {
	// @Override
	// public Comparator generate(ObjectComparator oc, Type t) {
	// if (!(t instanceof ParameterizedType))
	// return null;
	//
	// ParameterizedType pt = (ParameterizedType) t;
	// if (pt.getRawType() != List.class)
	// return null;
	// if (pt.getActualTypeArguments().length != 1)
	// throw new IllegalStateException();
	// Type et = pt.getActualTypeArguments()[0];
	// Comparator ec = oc.getComparator(et);
	//
	// return (p1, o1, p2, o2) -> {
	// List<?> l1 = (List<?>) o1;
	// List<?> l2 = (List<?>) o2;
	// if (l1.size() != l2.size()) {
	// System.err.println("DIFFERENTSIZE " + p1 + ":" + l1 + " " + p2 + ":" +
	// l2);
	// return;
	// }
	// Iterator<?> i1 = l1.iterator();
	// Iterator<?> i2 = l2.iterator();
	// int i = 0;
	// while (i1.hasNext()) {
	// Object le1 = i1.next();
	// Object le2 = i2.next();
	// ec.compare(p1.indexChild(i), le1, p1.indexChild(i), le2);
	// ++i;
	// }
	// };
	// }
	// };
	static CompararerFactory naturalEquals = new CompararerFactory() {

		@Override
		public Comparator generate(ObjectComparator<?> oc, Type t) {
			if (!(t instanceof Class))
				return null;
			Class<?> c = (Class<?>) t;

			try {
				Method method = c.getMethod("equals", Object.class);
				if (method == null)
					throw new IllegalStateException();
				if (method.getDeclaringClass() != c)
					return null;
				return (p1, o1, o2) -> {
					return o1.equals(o2);
				};
			} catch (NoSuchMethodException e) {
				return null;
			} catch (SecurityException e) {
				throw new Error(e);
			}
		}
	};

	// static CompararerFactory jaxbFields = new CompararerFactory() {
	//
	// @Override
	// public Comparator generate(ObjectComparator oc, Type t) {
	// if (!(t instanceof Class))
	// return null;
	// Class<?> c = (Class<?>) t;
	//
	// XmlAccessorType annotation = c.getAnnotation(XmlAccessorType.class);
	// if (annotation == null || annotation.value() != XmlAccessType.FIELD)
	// return null;
	//
	// if (c.getSuperclass() != Object.class)
	// throw new UnsupportedOperationException();
	//
	// Field[] fields = c.getDeclaredFields();
	// int idx = 0;
	// Comparator[] comarators = new Comparator[fields.length];
	// for (Field f : fields) {
	// f.setAccessible(true);
	// Comparator comparator = oc.getComparator(f.getGenericType());
	// comarators[idx++] = comparator;
	// }
	//
	// return (p1, o1, p2, o2) -> {
	// if (o1 == o2)
	// return;
	// if (o1 == null || o2 == null) {
	// System.err.println("uneq " + p1 + ":" + o1 + " " + p2 + ":" + o2);
	// return;
	// }
	//
	// for (int i = 0; i != comarators.length; ++i) {
	// Object n1;
	// Object n2;
	// try {
	// n1 = fields[i].get(o1);
	// n2 = fields[i].get(o2);
	// } catch (ReflectiveOperationException e) {
	// throw new RuntimeException(e);
	// }
	// comarators[i].compare(p1.child(fields[i].getName()), n1,
	// p2.child(fields[i].getName()), n2);
	// }
	// };
	// }
	// };

	// static CompararerFactory beanProperties = new CompararerFactory() {
	//
	// @Override
	// public Comparator generate(ObjectComparator oc, Type t) {
	// if (!(t instanceof Class))
	// return null;
	// Class<?> c = (Class<?>) t;
	//
	// try {
	// BeanInfo beanInfo = java.beans.Introspector.getBeanInfo(c);
	// PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
	//
	// if (pds.length == 0)
	// return null;
	//
	// int idx = 0;
	// Comparator[] comarators = new Comparator[pds.length];
	//
	// for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
	// System.err.println("pd=" + pd);
	// Comparator comparator =
	// oc.getComparator(pd.getReadMethod().getGenericReturnType());
	// comarators[idx++] = comparator;
	// }
	//
	// return (p1, o1, p2, o2) -> {
	// for (int i = 0; i != comarators.length; ++i) {
	// Object n1;
	// Object n2;
	// try {
	// n1 = pds[i].getReadMethod().invoke(o1);
	// n2 = pds[i].getReadMethod().invoke(o1);
	// } catch (InvocationTargetException e) {
	// throw new RuntimeException(e.getCause());
	// } catch (ReflectiveOperationException e) {
	// throw new RuntimeException(e);
	// }
	// comarators[i].compare(p1.child(pds[i].getName()), n1,
	// p2.child(pds[i].getName()), n2);
	// }
	// };
	// } catch (IntrospectionException e) {
	// throw new RuntimeException(e);
	// }
	// }
	//
	// };

	private Comparator findComparator(Type t) {
		for (CompararerFactory f : factories) {
			Comparator result = f.generate(this, t);
			if (result != null)
				return result;
		}

		return null;
	}

	private Decomposer<?> findDecomposer(Type t) {
		for (DecomposerFactory f : decomposerFactories) {
			Decomposer<?> result = f.generate(t);
			if (result != null)
				return result;
		}

		return null;
	}

	public static <T> Decomposer<List<T>> listToMapDecomposer(Function<T, String> keyRenderer) {
		return new Decomposer<List<T>>() {

			@Override
			public DecomposedObject decompose(List<T> o) {
				DecomposedObject result = new DecomposedObject();

				for (T e : o) {
					OPath key = OPath.index(keyRenderer.apply(e));

					result.put(key, e);
				}

				return result;
			}

		};
	}

	public enum ChangeType {
		CHANGED, ADDED, REMOVED;
	}

	public interface DeltaCreator<T> {
		T changed(OPath2 p, ChangeType change, Object m1, Object m2);

		default T missing(OPath2 p, Object m1) {
			return changed(p, ChangeType.REMOVED, m1, null);
		}

		default T additional(OPath2 p, Object m2) {
			return changed(p, ChangeType.ADDED, null, m2);
		}
	};

	private final DeltaCreator<T> deltaCreator;
	final static DeltaCreator<ObjectDelta> defaultDeltaCreator = (p, change, m1, m2) -> new ObjectDelta(p.getPath(),
			change, m1, m2);

	private void add(Consumer<T> sink, T delta) {
		if (delta != null)
			sink.accept(delta);
	}

	private void compare(OPath2 p, TypedObject m1, TypedObject m2, Consumer<T> sink) {
		if (m1.getValue() == m2.getValue())
			return;

		if (m1.getValue() == null || m2.getValue() == null) {
			add(sink, deltaCreator.changed(p, ChangeType.CHANGED, m1.getValue(), m2.getValue()));
			return;
		}

		Type t1 = m1.getType() != null ? m1.getType() : m1.getValue().getClass();

		// decompose

		@SuppressWarnings("rawtypes")
		Decomposer decomposer = null;

		for (Entry<DecomposerMatcher, DecomposerFactory> e : locationDecomposers.entrySet()) {
			if (e.getKey().apply(p, t1)) {
				decomposer = e.getValue().generate(t1);
				if (decomposer != null)
					break;
			}
		}
		if (decomposer == null)
			decomposer = findDecomposer(t1);

		if (decomposer != null) {

			@SuppressWarnings("unchecked")
			DecomposedObject d1 = decomposer.decompose(m1.getValue());
			@SuppressWarnings("unchecked")
			DecomposedObject d2 = decomposer.decompose(m2.getValue());

			for (OPath key : Sets.union(d1.keySet(), d2.keySet())) {

				// OPath child = key != null ? p.child(key) : p;

				Collection<TypedObject> c1 = d1.get(key);
				LinkedList<TypedObject> c2 = new LinkedList<>(d2.get(key));

				if (key != null && c1.size() == 1 && c2.size() == 1) {
					compare(p.child(key.path, Iterables.getOnlyElement(c1).getValue(),
							Iterables.getOnlyElement(c2).getValue()), Iterables.getOnlyElement(c1),
							Iterables.getOnlyElement(c2), sink);
					continue;
				}
				for (TypedObject v1 : c1) {

					boolean found = false;

					for (Iterator<TypedObject> i2 = c2.iterator(); i2.hasNext();) {

						List<T> temp = new ArrayList<>();

						TypedObject v2 = i2.next();
						compare(OPath2.root(v1, v2.getValue()), v1, v2, temp::add);

						if (temp.isEmpty()) {
							i2.remove();
							found = true;
							break;
						}
					}

					if (!found) {
						add(sink, deltaCreator.missing(key != null ? p.child(key.path, v1.getValue(), null) : p,
								v1.getValue()));
					}
				}

				for (TypedObject v2 : c2) {
					add(sink, deltaCreator.additional(key != null ? p.child(key.path, null, v2.getValue()) : p,
							v2.getValue()));
				}
			}

			return;
		}

		// compare
		for (Entry<Function<Type, Boolean>, BiFunction<Object, Object, Boolean>> e : comparators.entrySet()) {
			if (e.getKey().apply(t1)) {
				if (!e.getValue().apply(m1.getValue(), m2.getValue()))
					add(sink, deltaCreator.changed(p, ChangeType.CHANGED, m1.getValue(), m2.getValue()));
				return;
			}
		}
		Comparator comparator = findComparator(m1.getValue().getClass());
		if (comparator != null) {
			if (!comparator.compare(p, m1.getValue(), m2.getValue()))
				add(sink, deltaCreator.changed(p, ChangeType.CHANGED, m1.getValue(), m2.getValue()));
			return;
		}
		throw new Error("Don't know how to compare '" + t1 + "' / '" + m1.getValue().getClass().getSimpleName() + "'");
	}

	public List<T> compare(Object m1, Object m2) {
		List<T> result = new ArrayList<>();
		compare(OPath2.root(m1, m2), new TypedObject(m1), new TypedObject(m2), result::add);
		return result;
	}

}
