/*
 * Copyright (C) 2012-2015 DuyHai DOAN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.archinnov.achilles.internals.parser.validator;

import static com.google.auto.common.MoreTypes.asDeclared;
import static info.archinnov.achilles.internals.apt.AptUtils.containsAnnotation;
import static info.archinnov.achilles.internals.apt.AptUtils.getElementValueClassName;
import static info.archinnov.achilles.internals.parser.TypeUtils.ALLOWED_TYPES;
import static info.archinnov.achilles.internals.parser.validator.TypeValidator.validateAllowedTypes;
import static java.util.stream.Collectors.summingInt;
import static java.util.stream.Collectors.toList;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import info.archinnov.achilles.annotations.*;
import info.archinnov.achilles.internals.apt.AptUtils;
import info.archinnov.achilles.internals.metamodel.columns.KeyColumnInfo;
import info.archinnov.achilles.internals.parser.context.CodecContext;
import info.archinnov.achilles.internals.parser.context.FieldParsingContext;
import info.archinnov.achilles.type.tuples.Tuple2;


public class FieldValidator {

    public static void validateCompatibleColumnAnnotationsOnField(AptUtils aptUtils, String fieldName, TypeName rawEntityClass,
                                                                  Optional<PartitionKey> partitionKey, Optional<ClusteringColumn> clusteringColumn,
                                                                  Optional<Static> staticColumn, Optional<Computed> computed,
                                                                  Optional<Counter> counter) {

        aptUtils.validateFalse(partitionKey.isPresent() && staticColumn.isPresent(),
                "Field '%s' in class '%s' cannot be both partition key AND static column", fieldName, rawEntityClass);
        aptUtils.validateFalse(clusteringColumn.isPresent() && staticColumn.isPresent(),
                "Field '%s' in class '%s' cannot be both clustering column AND static column", fieldName, rawEntityClass);
        aptUtils.validateFalse(partitionKey.isPresent() && clusteringColumn.isPresent(),
                "Field '%s' in class '%s' cannot be both partition key AND clustering column", fieldName, rawEntityClass);
        aptUtils.validateFalse(partitionKey.isPresent() && computed.isPresent(),
                "Field '%s' in class '%s' cannot be both partition key AND computed column", fieldName, rawEntityClass);
        aptUtils.validateFalse(clusteringColumn.isPresent() && computed.isPresent(),
                "Field '%s' in class '%s' cannot be both clustering column AND computed column", fieldName, rawEntityClass);
        aptUtils.validateFalse(staticColumn.isPresent() && computed.isPresent(),
                "Field '%s' in class '%s' cannot be both static column AND computed column", fieldName, rawEntityClass);

        aptUtils.validateFalse(partitionKey.isPresent() && counter.isPresent(),
                "Field '%s' in class '%s' cannot be both partition key AND counter column", fieldName, rawEntityClass);
        aptUtils.validateFalse(clusteringColumn.isPresent() && counter.isPresent(),
                "Field '%s' in class '%s' cannot be both clustering column AND counter column", fieldName, rawEntityClass);
        aptUtils.validateFalse(computed.isPresent() && counter.isPresent(),
                "Field '%s' in class '%s' cannot be both computed column AND counter column", fieldName, rawEntityClass);
    }

    public static void validateCompatibleCodecAnnotationsOnField(AptUtils aptUtils, String fieldName, Name className,
                                                                 Frozen frozen, JSON json, Enumerated enumerated, Codec codec, Computed computed, Counter counter, TimeUUID timeUUID) {
        aptUtils.validateFalse((json != null) && (codec != null), "Cannot have both @JSON and @Codec annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((enumerated != null) && (codec != null), "Cannot have both @Enumerated and @Codec annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((enumerated != null) && (json != null), "Cannot have both @Enumerated and @JSON annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((frozen != null) && (json != null), "Cannot have both @Frozen and @JSON annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((frozen != null) && (enumerated != null), "Cannot have both @Frozen and @Enumerated annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((frozen != null) && (codec != null), "Cannot have both @Frozen and @Codec annotation on the same field '%s' in class '%s'", fieldName, className);

        aptUtils.validateFalse((frozen != null) && (computed != null), "Cannot have both @Frozen and @Computed annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((json != null) && (computed != null), "Cannot have both @JSON and @Computed annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((enumerated != null) && (computed != null), "Cannot have both @Enumerated and @Computed annotation on the same field '%s' in class '%s'", fieldName, className);

        aptUtils.validateFalse((frozen != null) && (counter != null), "Cannot have both @Frozen and @Counter annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((json != null) && (counter != null), "Cannot have both @JSON and @Counter annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((enumerated != null) && (counter != null), "Cannot have both @Enumerated and @Counter annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((computed != null) && (counter != null), "Cannot have both @Computed and @Counter annotation on the same field '%s' in class '%s'", fieldName, className);

        aptUtils.validateFalse((frozen != null) && (timeUUID != null), "Cannot have both @Frozen and @TimeUUID annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((json != null) && (timeUUID != null), "Cannot have both @JSON and @TimeUUID annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((enumerated != null) && (timeUUID != null), "Cannot have both @Enumerated and @TimeUUID annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((codec != null) && (timeUUID != null), "Cannot have both @Codec and @TimeUUID annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((computed != null) && (timeUUID != null), "Cannot have both @Computed and @TimeUUID annotation on the same field '%s' in class '%s'", fieldName, className);
        aptUtils.validateFalse((counter != null) && (timeUUID != null), "Cannot have both @Counter and @TimeUUID annotation on the same field '%s' in class '%s'", fieldName, className);
    }

    public static void validateAllowedFrozen(Optional<Frozen> frozen, AptUtils aptUtils, VariableElement elm, String fieldName, TypeName rawClass) {
        if (frozen.isPresent()) {
            aptUtils.validateTrue(aptUtils.isCompositeType(elm.asType()),
                    "@Frozen annotation only field '%s' of class '%s' is only allowed for collections an UDT ",
                    fieldName, rawClass);
        }
    }

    public static void validateAllowedType(AptUtils aptUtils, TypeName rawTargetType, /* List<AnnotationMirror> annotations,  TypeMirror currentTypeMirror, */ FieldParsingContext context) {
        aptUtils.validateTrue(ALLOWED_TYPES.contains(rawTargetType), //|| hasTransformation,
                "Impossible to parse type '%s' from field '%s' of class '%s'. It should be a supported type",
                rawTargetType.toString(), context.fieldName, context.className);
    }

    public static void validateCounter(AptUtils aptUtils, TypeName targetType, List<AnnotationMirror> annotations, FieldParsingContext context) {
        if (containsAnnotation(annotations, Counter.class)) {
            aptUtils.validateTrue(targetType.box().equals(TypeName.LONG.box()),
                    "Field '%s' of class '%s' annotated with @Counter should be of type Long/long",
                    context.fieldName, context.className);
        }
    }

    public static void validateCorrectKeysOrder(AptUtils aptUtils, TypeName rawClassName, List<Tuple2<String, KeyColumnInfo>> keyTuples, String type) {
        /**
         * Math formula : sum of N consecutive integers = N * (N+1)/2
         */
        int checkForKeyOrdering = (keyTuples.size() * (keyTuples.size() + 1)) / 2;
        final Integer sumOfOrders = keyTuples.stream()
                .map(x -> x._2())
                .collect(summingInt(x -> x.order()));
        aptUtils.validateTrue(checkForKeyOrdering == sumOfOrders, "The %s ordering is wrong in class '%s'", type, rawClassName);
    }

    public static CodecContext validateCodec(AptUtils aptUtils, AnnotationMirror codecFromType, TypeName sourceType,
                                             Optional<TypeName> cqlClass, boolean isCounter) {

        final CodecContext codecContext = getCodecTypes(aptUtils, codecFromType);
        final String codecClass = codecContext.codecType.toString();

        aptUtils.validateTrue(sourceType.box().equals(codecContext.sourceType.box()), "Codec '%s' source type '%s' should match current object type '%s'",
                codecClass, codecContext.sourceType, sourceType.toString());
        if (cqlClass.isPresent()) {
            aptUtils.validateTrue(codecContext.targetType.box().equals(cqlClass.get().box()), "Codec '%s' target type '%s' should match computed CQL type '%s'",
                    codecClass, codecContext.targetType, cqlClass.get());
        }
        if (isCounter) {
            aptUtils.validateTrue(codecContext.targetType.box().equals(TypeName.LONG.box()),
                    "Codec '%s' target type '%s' should be Long/long because the column is annotated with @Counter",
                    codecClass, codecContext.targetType);
        }
        validateAllowedTypes(aptUtils, sourceType, codecContext.targetType);

        return codecContext;
    }

    private static CodecContext getCodecTypes(AptUtils aptUtils, AnnotationMirror codecFromType) {
        Optional<Class<Codec>> codecClassO = AptUtils.getElementValueClass(codecFromType, "value", false);
        if (codecClassO.isPresent()) {
            Class<Codec> codecClass = codecClassO.get();
            List<Type> genericTypes = Arrays.asList(codecClass.getGenericInterfaces());

            final List<TypeName> codecTypes = genericTypes
                    .stream()
                    .filter(x -> x instanceof ParameterizedType)
                    .map(x -> (ParameterizedType) x)
                    .filter(x -> x.getRawType().getTypeName().equals(info.archinnov.achilles.type.codec.Codec.class.getCanonicalName()))
                    .flatMap(x -> Arrays.asList(x.getActualTypeArguments()).stream())
                    .map(TypeName::get)
                    .collect(Collectors.toList());
            aptUtils.validateTrue(codecTypes.size() == 2, "Codec class '%s' should have 2 parameters: Codec<FROM, TO>", codecClass);
            return new CodecContext(ClassName.get(codecClass), codecTypes.get(0), codecTypes.get(1));
        } else {
            final TypeMirror codecInterfaceType = aptUtils.erasure(aptUtils.elementUtils.getTypeElement(info.archinnov.achilles.type.codec.Codec.class.getCanonicalName()).asType());
            final Optional<? extends TypeMirror> foundCodecInterface = aptUtils.elementUtils.getTypeElement(getElementValueClassName(codecFromType, "value", false))
                    .getInterfaces()
                    .stream()
                    .filter(x -> aptUtils.typeUtils.isSameType(aptUtils.erasure(x), codecInterfaceType))
                    .findFirst();

            aptUtils.validateTrue(foundCodecInterface.isPresent(), "Codec class '%s' should implement the Codec<FROM, TO> interface", codecFromType);

            final TypeMirror typeMirror = foundCodecInterface.get();

            final List<TypeName> codecTypes = asDeclared(typeMirror)
                    .getTypeArguments()
                    .stream()
                    .map(TypeName::get)
                    .collect(toList());
            aptUtils.validateTrue(codecTypes.size() == 2, "Codec class '%s' should have 2 parameters: Codec<FROM, TO>", codecInterfaceType);

            final TypeMirror codecType = aptUtils.erasure(aptUtils.elementUtils.getTypeElement(getElementValueClassName(codecFromType, "value", false)));

            return new CodecContext(TypeName.get(codecType), codecTypes.get(0), codecTypes.get(1));
        }
    }
}
