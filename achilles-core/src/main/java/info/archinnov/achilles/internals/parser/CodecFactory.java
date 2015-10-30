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

package info.archinnov.achilles.internals.parser;

import static info.archinnov.achilles.internals.apt.AptUtils.*;
import static info.archinnov.achilles.internals.parser.TypeUtils.*;
import static info.archinnov.achilles.internals.parser.validator.FieldValidator.validateCodec;
import static info.archinnov.achilles.internals.parser.validator.TypeValidator.validateAllowedTypes;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.apache.commons.lang3.ArrayUtils;

import com.squareup.javapoet.*;

import info.archinnov.achilles.annotations.*;
import info.archinnov.achilles.annotations.Enumerated.Encoding;
import info.archinnov.achilles.internals.apt.AptUtils;
import info.archinnov.achilles.internals.parser.context.CodecContext;
import info.archinnov.achilles.internals.parser.context.FieldParsingContext;
import info.archinnov.achilles.type.tuples.Tuple2;


public class CodecFactory {

    private final Elements elementUtils;
    private final Types typeUtils;
    private final AptUtils aptUtils;


    public CodecFactory(AptUtils aptUtils) {
        this.elementUtils = aptUtils.elementUtils;
        this.typeUtils = aptUtils.typeUtils;
        this.aptUtils = aptUtils;
    }

    public CodecInfo createCodec(TypeName sourceType, AnnotationTree annotationTree, FieldParsingContext context) {
        final String fieldName = context.fieldName;
        final String className = context.className;

        TypeName targetType = sourceType;
        TypeMirror typeMirror = annotationTree.getCurrentType();

        final List<AnnotationMirror> annotations = annotationTree.getAnnotations();
        final Optional<? extends AnnotationMirror> jsonTransform = extractAnnotation(annotations, JSON.class);
        final Optional<? extends AnnotationMirror> enumerated = extractAnnotation(annotations, Enumerated.class);
        final Optional<? extends AnnotationMirror> codecFromType = extractAnnotation(annotations, Codec.class);
        final Optional<Codec> codecFromClass = getOptionalCodecFromClass(typeMirror);
        final Optional<? extends AnnotationMirror> computed = extractAnnotation(annotations, Computed.class);
        final Optional<TypeName> computedCQLClass = computed
                .flatMap(x -> getElementValueClass(x, "cqlClass", false))
                .map(x -> ClassName.get(x));
        final boolean isCounter = extractAnnotation(annotations, Counter.class).isPresent();

        CodeBlock codec;

        if (jsonTransform.isPresent()) {
            codec = CodeBlock.builder().add("new $T<>($T.class, $L)", JSON_CODEC, getRawType(sourceType).box(), buildJavaTypeForJackson(sourceType)).build();
            targetType = ClassName.get(String.class);
        } else if (codecFromType.isPresent()) {
            final Tuple2<TypeName, CodeBlock> tuple2 = buildCodecFromType(codecFromType.get(), sourceType, computedCQLClass, isCounter);
            targetType = tuple2._1();
            codec = tuple2._2();
        } else if (codecFromClass.isPresent()) {
            final Tuple2<TypeName, CodeBlock> tuple2 = buildCodecFromClass(codecFromClass.get(), sourceType, computedCQLClass, isCounter);
            targetType = tuple2._1();
            codec = tuple2._2();
        } else if (enumerated.isPresent()) {
            final Tuple2<TypeName, CodeBlock> tuple2 = buildEnumeratedCodec(typeMirror, enumerated.get(), sourceType, fieldName, className);
            codec = tuple2._2();
            targetType = tuple2._1();
        } else if (typeMirror.getKind() == TypeKind.ARRAY && typeMirror.toString().equals("byte[]")) {
            codec = CodeBlock.builder().add("new $T()", BYTE_ARRAY_PRIMITIVE_CODEC).build();
            targetType = BYTE_BUFFER;
        } else if (typeMirror.getKind() == TypeKind.ARRAY && typeMirror.toString().equals("java.lang.Byte[]")) {
            codec = CodeBlock.builder().add("new $T()", BYTE_ARRAY_CODEC).build();
            targetType = BYTE_BUFFER;
        } else {
            if (computedCQLClass.isPresent()) {
                aptUtils.validateTrue(sourceType.equals(computedCQLClass.get()),
                        "CQL class '%s' of @Computed field '%s' of class '%s' should be same as field class '%s'",
                        computedCQLClass.get(), fieldName, className, sourceType);
            }
            validateAllowedTypes(aptUtils, sourceType, sourceType);
            codec = CodeBlock.builder().add("new $T<>($T.class)", FALL_THROUGH_CODEC, getRawType(sourceType).box()).build();
        }
        return new CodecInfo(codec, sourceType, targetType);
    }


    private List<TypeName> getCodecTypes(Class<? extends info.archinnov.achilles.type.codec.Codec> codecClass) {
        List<Type> genericTypes = Arrays.asList(codecClass.getGenericInterfaces());

        final List<TypeName> codecTypes = genericTypes
                .stream()
                .filter(x -> x instanceof ParameterizedType)
                .map(x -> (ParameterizedType) x)
                .filter(x -> x.getRawType().getTypeName().equals(info.archinnov.achilles.type.codec.Codec.class.getCanonicalName()))
                .flatMap(x -> Arrays.asList(x.getActualTypeArguments()).stream())
                .map(TypeName::get)
                .collect(Collectors.toList());

        return codecTypes;
    }

    private List<TypeName> getCodecTypes(TypeMirror typeMirror) {
        return aptUtils.getInterfaces(typeMirror)
                .stream()
                .filter(x -> aptUtils.erasure(x).toString().equals(info.archinnov.achilles.type.codec.Codec.class.getCanonicalName()))
                .flatMap(x -> AptUtils.getTypeArguments(x).stream())
                .map(TypeName::get)
                .collect(Collectors.toList());
    }

    private Tuple2<TypeName, CodeBlock> buildCodecFromType(AnnotationMirror codecFromType, TypeName sourceType,
                                                           Optional<TypeName> cqlClass, boolean isCounter) {
        final CodecContext codecContext = validateCodec(aptUtils, codecFromType, sourceType, cqlClass, isCounter);
        CodeBlock codec = CodeBlock.builder().add("new $T()", codecContext.codecType).build();

        return new Tuple2<>(codecContext.targetType.box(), codec);
    }

    private Tuple2<TypeName, CodeBlock> buildCodecFromClass(Codec codecFromClass, TypeName sourceType, Optional<TypeName> cqlClass, boolean isCounter) {
        List<TypeName> codecTypes;
        String codecClassName;
        TypeName codecTypeName;
        try {
            final Class<? extends info.archinnov.achilles.type.codec.Codec> codecClass = codecFromClass.value();
            codecClassName = codecClass.getCanonicalName();
            codecTypes = getCodecTypes(codecClass);
            codecTypeName = TypeName.get(codecClass);
        } catch (MirroredTypeException ex) {
            final TypeMirror codecTypeMirror = ex.getTypeMirror();
            codecClassName = codecTypeMirror.toString();
            codecTypes = getCodecTypes(codecTypeMirror);
            codecTypeName = TypeName.get(codecTypeMirror);
        }

        aptUtils.validateTrue(codecTypes.size() == 2, "Codec class '%s' should have 2 parameters: Codec<FROM, TO>", codecClassName);
        TypeName codecSourceType = codecTypes.get(0);
        TypeName targetType = codecTypes.get(1);
        aptUtils.validateTrue(sourceType.equals(codecSourceType), "Codec '%s' source type '%s' should match current object type '%s'",
                codecClassName, codecSourceType.toString(), sourceType.toString());
        if (cqlClass.isPresent()) {
            aptUtils.validateTrue(targetType.equals(cqlClass.get()), "Codec '%s' target type '%s' should match computed CQL type '%s'",
                    codecClassName, codecSourceType.toString(), sourceType.toString());
        }
        if (isCounter) {
            aptUtils.validateTrue(targetType.box().equals(TypeName.LONG.box()),
                    "Codec '%s' target type '%s' should be Long/long because the column is annotated with @Counter",
                    codecClassName, targetType);
        }
        validateAllowedTypes(aptUtils, sourceType, targetType);
        CodeBlock codec = CodeBlock.builder().add("new $T()", codecTypeName).build();

        return new Tuple2<>(targetType, codec);
    }

    private Tuple2<TypeName, CodeBlock> buildEnumeratedCodec(TypeMirror typeMirror, AnnotationMirror enumerated, TypeName sourceType, String fieldName, String className) {
        typeUtils.isAssignable(typeMirror, elementUtils.getTypeElement(Enum.class.getCanonicalName()).asType());

        aptUtils.validateTrue(isAnEnum(typeMirror), "The type '%s' on field '%s' in class '%s' is not a java.lang.Enum type",
                sourceType.toString(), fieldName, className);
        final Encoding encoding = getElementValueEnum(enumerated, "value", Encoding.class, true);
        if (encoding == Encoding.NAME) {
            return new Tuple2<>(STRING, CodeBlock.builder()
                    .add("new $T<>(java.util.Arrays.asList($T.values()), $T.class)", ENUM_NAME_CODEC, sourceType, sourceType.box())
                    .build());

        } else {
            return new Tuple2<>(OBJECT_INT, CodeBlock.builder()
                    .add("new $T<>(java.util.Arrays.asList($T.values()), $T.class)", ENUM_ORDINAL_CODEC, sourceType, sourceType.box())
                    .build());
        }
    }

    CodeBlock buildJavaTypeForJackson(TypeName sourceType) {
        if (sourceType instanceof ClassName) {
            final ClassName className = (ClassName) sourceType;
            return CodeBlock.builder().add("$T.construct($T.class)", SIMPLE_TYPE, className.box()).build();

        } else if (sourceType instanceof ParameterizedTypeName) {
            final ParameterizedTypeName paramTypeName = (ParameterizedTypeName) sourceType;

            StringJoiner code = new StringJoiner(",",
                    "$T.TYPE_FACTORY_INSTANCE.constructParametricType($T.class,",
                    ")");
            paramTypeName.typeArguments.forEach(x -> code.add("$L"));

            final Object[] headTypes = new Object[]{JSON_CODEC, paramTypeName.rawType};
            final Object[] codeBlocks = paramTypeName.typeArguments
                    .stream()
                    .map(typeName -> (Object) buildJavaTypeForJackson(typeName))
                    .toArray();

            return CodeBlock.builder().add(code.toString(),
                    ArrayUtils.addAll(headTypes, codeBlocks)).build();

        } else if (sourceType instanceof ArrayTypeName) {
            final TypeName componentType = ((ArrayTypeName) sourceType).componentType;
            return CodeBlock.builder().add("$T.TYPE_FACTORY_INSTANCE.constructArrayType($L)",
                    JSON_CODEC, buildJavaTypeForJackson(componentType.box())).build();

        } else if (sourceType instanceof WildcardTypeName) {
            aptUtils.printError("Cannot build Jackson Mapper JavaType for wildcard type " + sourceType.toString());
        } else {
            aptUtils.printError("Cannot build Jackson Mapper JavaType for type " + sourceType.toString());
        }


        return null;
    }

    protected static final class CodecInfo {

        final protected CodeBlock codecCode;
        final protected TypeName sourceType;
        final protected TypeName targetType;

        public CodecInfo(CodeBlock codecCode, TypeName sourceType, TypeName targetType) {
            this.codecCode = codecCode;
            this.sourceType = sourceType;
            this.targetType = targetType;
        }
    }

}
