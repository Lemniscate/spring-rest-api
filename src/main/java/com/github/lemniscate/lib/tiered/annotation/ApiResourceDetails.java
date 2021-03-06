package com.github.lemniscate.lib.tiered.annotation;

import lombok.Getter;
import org.hibernate.annotations.ManyToAny;
import org.springframework.core.GenericCollectionTypeResolver;
import org.springframework.hateoas.Identifiable;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collection;

/**
 * Convenience class to encapsulate the data between {@link ApiResource} and {@link ApiNestedResource}.
 */
@Getter
public final class ApiResourceDetails<E extends Identifiable<ID>, ID extends Serializable, B> {

    private final Class<ID> idClass;
    private final Class<E> domainClass;
    private final Class<? extends Identifiable<ID>> parentClass;
    private final Class<?> beanClass;
    private final String name, path, parentProperty;

    public ApiResourceDetails(ApiResource resource, Class<?> domainClass){
        this.name = getPath(domainClass);
        this.path = "/" + name;

        this.idClass = (Class<ID>) resource.idClass();
        this.domainClass = (Class<E>) domainClass;
        this.beanClass = resource.beanClass().equals( Object.class )
            ? domainClass
            : resource.beanClass();
        this.parentClass = null;
        this.parentProperty = null;
    }

    public ApiResourceDetails(ApiNestedResource resource, Class<?> domainClass){
        this.name = getPath(domainClass);

        String parentProperty = resource.parentProperty();
        this.parentClass = (Class<? extends Identifiable<ID>>) ReflectionUtils.findField(domainClass, parentProperty).getType();

        String parentName = getPath(parentClass);
        this.path = "/" + parentName + "/{peId}/" + this.name;

        this.idClass = (Class<ID>) resource.idClass();
        this.domainClass = (Class<E>) domainClass;
        this.beanClass = resource.beanClass().equals( Object.class )
                ? domainClass
                : resource.beanClass();

        this.parentProperty = resource.parentProperty();
    }

    public static <E extends Identifiable<ID>, ID extends Serializable, B> ApiResourceDetails<E, ID, B> from(Class<?> domainClass){

        ApiResource ar = domainClass.getAnnotation(ApiResource.class);
        ApiNestedResource anr = domainClass.getAnnotation(ApiNestedResource.class);

        ApiResourceDetails wrapper = null;
        if( ar != null ){
            wrapper = new ApiResourceDetails(ar, domainClass);
        }else if( anr != null ){
            wrapper = new ApiResourceDetails(anr, domainClass);
        }else{
            // look for an ancestor who might be a resource
            Class<?> superClass = domainClass.getSuperclass();
            if( superClass != null && !Object.class.equals( superClass )){
                wrapper = from( domainClass.getSuperclass() );
            }
        }
        return wrapper;
    }

    public static <E extends Identifiable<ID>, ID extends Serializable, B> ApiResourceDetails<E, ID, B> from(Field field){
        Class<?> domainClass = field.getType();
        if( Collection.class.isAssignableFrom(domainClass) ){
            domainClass = GenericCollectionTypeResolver.getCollectionFieldType(field);
        }
        return from(domainClass);
    };

    public Class<?> getParentIdClass(){
        Assert.notNull(parentClass, "No parent class found");
        Class<?> pid = ApiResourceDetails.from( parentClass ).getIdClass();
        return pid;
    }

    public boolean isNested(){
        return parentClass != null;
    }

    public boolean isNestedCollection(){
        if( !isNested() ){
            return false;
        }

        ApiNestedResource anr = domainClass.getAnnotation(ApiNestedResource.class);
        Assert.notNull(anr, "Not a nested resource");

        String parentProperty = anr.parentProperty();
        Field field = ReflectionUtils.findField(domainClass, parentProperty);
        if( hasAnnotation(field, OneToOne.class, org.hibernate.mapping.OneToOne.class) ){
            return false;
        }else if( hasAnnotation(field, ManyToOne.class, org.hibernate.mapping.ManyToOne.class,
                ManyToMany.class, ManyToAny.class) ){ // TODO handle more mappings here?
            return true;
        }

        throw new IllegalStateException("No known mapping found");

    }

    private boolean hasAnnotation( Field field, Class<?>... annotations){

        for( Class<?> a : annotations ){
            if( field.isAnnotationPresent( (Class<Annotation>) a) ){
                return true;
            }
        }
        return false;
    }

    public static String getPath(Class<?> domainClass){
        ApiResource ar = domainClass.getAnnotation(ApiResource.class);
        ApiNestedResource anr = domainClass.getAnnotation(ApiNestedResource.class);

        String result;
        if( ar != null ){
            result = ar.path();
        }else if( anr != null){
            result = anr.path();
        }else{
            throw new IllegalStateException("Not an entity");
        }

        if( result == null || result.trim().isEmpty() ){
            result = domainClass.getSimpleName();
            result = result.toLowerCase().charAt(0) + result.substring(1) + "s";
        }

        return result;
    };

}
