/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen.languages;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import org.openapitools.codegen.*;
import org.openapitools.codegen.meta.GeneratorMetadata;
import org.openapitools.codegen.meta.Stability;
import org.openapitools.codegen.meta.features.*;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;

import java.io.File;
import java.util.*;

import static org.openapitools.codegen.utils.StringUtils.camelize;

/**
 * Generates a TypeScript client library using Redux Toolkit Query (RTK Query).
 *
 * <p>Each OpenAPI tag is turned into a {@code createApi()} slice. Endpoints are
 * typed as {@code builder.query} (GET / HEAD) or {@code builder.mutation}
 * (POST / PUT / PATCH / DELETE). React hooks are exported by default.</p>
 */
public class TypeScriptRtkQueryClientCodegen extends AbstractTypeScriptClientCodegen {

    public static final String NAME = "typescript-rtk-query";

    // ── Option keys ───────────────────────────────────────────────────────────
    public static final String WITH_REACT_HOOKS             = "withReactHooks";
    public static final String REDUCER_PATH                 = "reducerPath";
    public static final String ADD_TAG_TYPES                = "addTagTypes";
    public static final String GENERATE_STORE               = "generateStore";
    public static final String USE_SINGLE_REQUEST_PARAMETER = "useSingleRequestParameter";
    public static final String IMPORT_FILE_EXTENSION        = "importFileExtension";
    public static final String NPM_REPOSITORY               = "npmRepository";

    // ── Option defaults ───────────────────────────────────────────────────────
    protected boolean withReactHooks             = true;
    protected String  reducerPath                = "";   // "" → derive from tag name
    protected boolean addTagTypes                = true;
    protected boolean generateStore              = false;
    protected boolean useSingleRequestParameter  = true;
    protected String  importFileExtension        = "";
    protected String  npmRepository              = null;

    // Track whether index files have been scheduled already
    protected boolean addedApiIndex   = false;
    protected boolean addedModelIndex = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    public TypeScriptRtkQueryClientCodegen() {
        super();

        // ── Generator metadata ────────────────────────────────────────────────
        generatorMetadata = GeneratorMetadata.newBuilder(generatorMetadata)
                .stability(Stability.BETA)
                .build();

        modifyFeatureSet(features -> features
                .includeDocumentationFeatures(DocumentationFeature.Readme)
                .wireFormatFeatures(EnumSet.of(WireFormatFeature.JSON))
                .securityFeatures(EnumSet.of(
                        SecurityFeature.BasicAuth,
                        SecurityFeature.BearerToken,
                        SecurityFeature.ApiKey,
                        SecurityFeature.OAuth2_Implicit,
                        SecurityFeature.OAuth2_AuthorizationCode,
                        SecurityFeature.OAuth2_ClientCredentials,
                        SecurityFeature.OAuth2_Password))
                .excludeGlobalFeatures(
                        GlobalFeature.XMLStructureDefinitions,
                        GlobalFeature.Callbacks,
                        GlobalFeature.LinkObjects,
                        GlobalFeature.ParameterStyling)
                .excludeSchemaSupportFeatures(SchemaSupportFeature.Polymorphism)
                .excludeParameterFeatures(ParameterFeature.Cookie)
                .includeClientModificationFeatures(ClientModificationFeature.BasePath)
        );

        // Clear default import mapping – TS generators manage this themselves
        importMapping.clear();

        supportsMultipleInheritance = true;

        // ── Output paths ──────────────────────────────────────────────────────
        outputFolder        = "generated-code/typescript-rtk-query";
        embeddedTemplateDir = templateDir = "typescript-rtk-query";

        // ── Template → file mapping ───────────────────────────────────────────
        apiTemplateFiles.put("apis.mustache",   ".ts");
        modelTemplateFiles.put("models.mustache", ".ts");

        // ── Type mappings ─────────────────────────────────────────────────────
        typeMapping.put("date",     "Date");
        typeMapping.put("DateTime", "Date");

        // ── Model property naming ─────────────────────────────────────────────
        supportModelPropertyNaming(CodegenConstants.MODEL_PROPERTY_NAMING_TYPE.camelCase);

        // ── CLI Options ───────────────────────────────────────────────────────
        this.cliOptions.add(new CliOption(NPM_REPOSITORY,
                "URL of a private npm registry to publish the generated package to."));

        this.cliOptions.add(new CliOption(WITH_REACT_HOOKS,
                "Export typed React hooks (useXxxQuery / useXxxMutation) for every endpoint.",
                SchemaTypeUtil.BOOLEAN_TYPE)
                .defaultValue(Boolean.TRUE.toString()));

        this.cliOptions.add(new CliOption(REDUCER_PATH,
                "Value used for createApi() reducerPath. Defaults to '<tagName>Api' when empty."));

        this.cliOptions.add(new CliOption(ADD_TAG_TYPES,
                "Add a tagTypes array to createApi() to enable automatic cache invalidation.",
                SchemaTypeUtil.BOOLEAN_TYPE)
                .defaultValue(Boolean.TRUE.toString()));

        this.cliOptions.add(new CliOption(GENERATE_STORE,
                "Generate an example store.ts that wires all API reducers and middleware.",
                SchemaTypeUtil.BOOLEAN_TYPE)
                .defaultValue(Boolean.FALSE.toString()));

        this.cliOptions.add(new CliOption(USE_SINGLE_REQUEST_PARAMETER,
                "Wrap all endpoint parameters in a single typed request-object interface.",
                SchemaTypeUtil.BOOLEAN_TYPE)
                .defaultValue(Boolean.TRUE.toString()));

        this.cliOptions.add(new CliOption(IMPORT_FILE_EXTENSION,
                "File extension appended to relative import paths. Set to '.js' or '.mjs' for ESM."));

        this.addExtraReservedWords();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Identity
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String getName() { return NAME; }

    @Override
    public String getHelp() {
        return "Generates a TypeScript client using Redux Toolkit Query (RTK Query). " +
               "Each OpenAPI tag becomes a createApi() slice. Endpoints are typed as " +
               "builder.query (GET/HEAD) or builder.mutation (POST/PUT/PATCH/DELETE). " +
               "React hooks (useXxxQuery / useXxxMutation) are exported by default.";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Option processing
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void processOpts() {
        super.processOpts();

        additionalProperties.put("isOriginalModelPropertyNaming",
                getModelPropertyNaming() == CodegenConstants.MODEL_PROPERTY_NAMING_TYPE.original);
        additionalProperties.put("modelPropertyNaming", getModelPropertyNaming().name());

        // withReactHooks
        if (additionalProperties.containsKey(WITH_REACT_HOOKS)) {
            this.withReactHooks = convertPropertyToBoolean(WITH_REACT_HOOKS);
        }
        writePropertyBack(WITH_REACT_HOOKS, this.withReactHooks);

        // reducerPath
        if (additionalProperties.containsKey(REDUCER_PATH)) {
            this.reducerPath = additionalProperties.get(REDUCER_PATH).toString();
        }
        writePropertyBack(REDUCER_PATH, this.reducerPath);

        // addTagTypes
        if (additionalProperties.containsKey(ADD_TAG_TYPES)) {
            this.addTagTypes = convertPropertyToBoolean(ADD_TAG_TYPES);
        }
        writePropertyBack(ADD_TAG_TYPES, this.addTagTypes);

        // generateStore
        if (additionalProperties.containsKey(GENERATE_STORE)) {
            this.generateStore = convertPropertyToBoolean(GENERATE_STORE);
        }
        writePropertyBack(GENERATE_STORE, this.generateStore);

        // useSingleRequestParameter
        if (additionalProperties.containsKey(USE_SINGLE_REQUEST_PARAMETER)) {
            this.useSingleRequestParameter = convertPropertyToBoolean(USE_SINGLE_REQUEST_PARAMETER);
        }
        writePropertyBack(USE_SINGLE_REQUEST_PARAMETER, this.useSingleRequestParameter);

        // importFileExtension
        if (additionalProperties.containsKey(IMPORT_FILE_EXTENSION)) {
            this.importFileExtension = additionalProperties.get(IMPORT_FILE_EXTENSION).toString();
        }
        writePropertyBack(IMPORT_FILE_EXTENSION, this.importFileExtension);

        // npmRepository (only relevant when npmName is also set)
        if (additionalProperties.containsKey(NPM_REPOSITORY)) {
            this.npmRepository = additionalProperties.get(NPM_REPOSITORY).toString();
        }

        // ── Output folder layout ──────────────────────────────────────────────
        this.apiPackage   = "src" + File.separator + "apis";
        this.modelPackage = "src" + File.separator + "models";

        // ── Static supporting files ───────────────────────────────────────────
        supportingFiles.add(new SupportingFile("index.mustache",     "src", "index.ts"));
        supportingFiles.add(new SupportingFile("runtime.mustache",   "src", "runtime.ts"));
        supportingFiles.add(new SupportingFile("tsconfig.mustache",  "",    "tsconfig.json"));
        supportingFiles.add(new SupportingFile("gitignore",          "",    ".gitignore"));
        supportingFiles.add(new SupportingFile("licenseInfo.mustache", "", ".openapi-generator-info"));

        if (generateStore) {
            supportingFiles.add(new SupportingFile("store.mustache", "src", "store.ts"));
        }

        // ── npm package files (only when npmName is provided) ─────────────────
        if (additionalProperties.containsKey(NPM_NAME)) {
            supportingFiles.add(new SupportingFile("README.mustache",   "", "README.md"));
            supportingFiles.add(new SupportingFile("package.mustache",  "", "package.json"));
            supportingFiles.add(new SupportingFile("npmignore.mustache","", ".npmignore"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Type declarations
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("rawtypes")
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isFileSchema(p) || ModelUtils.isBinarySchema(p)) {
            return "Blob";
        }
        return super.getTypeDeclaration(p);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operation enrichment  (vendor extensions used by templates)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod,
                                          Operation operation, List<Server> servers) {
        CodegenOperation op = super.fromOperation(path, httpMethod, operation, servers);

        boolean isQuery = isReadOperation(httpMethod);

        // builder.query vs builder.mutation
        op.vendorExtensions.put("x-rtk-is-query",    isQuery);
        op.vendorExtensions.put("x-rtk-is-mutation", !isQuery);

        // Hook name:  use{PascalOperationId}Query  |  use{PascalOperationId}Mutation
        String pascal     = camelize(op.operationId);
        String hookSuffix = isQuery ? "Query" : "Mutation";
        op.vendorExtensions.put("x-rtk-hook-name", "use" + pascal + hookSuffix);

        return op;
    }

    /**
     * GET and HEAD are safe / idempotent read operations → {@code builder.query}.
     * Everything else → {@code builder.mutation}.
     */
    private boolean isReadOperation(String httpMethod) {
        return "GET".equalsIgnoreCase(httpMethod) || "HEAD".equalsIgnoreCase(httpMethod);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operations map post-processing
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs,
                                                         List<ModelMap> allModels) {
        objs = super.postProcessOperationsWithModels(objs, allModels);

        OperationMap operations = objs.getOperations();
        // classname arrives as e.g. "PetApi" (tag + "Api" suffix from AbstractTypeScriptClientCodegen)
        String classname = operations.getClassname();

        // Strip trailing "Api" to get the bare tag name (e.g. "Pet")
        String tagName = classname.endsWith("Api")
                ? classname.substring(0, classname.length() - 3)
                : classname;

        // reducerPath: use explicit option or derive "<tagName lower-first>Api"
        String rPath = (reducerPath == null || reducerPath.isEmpty())
                ? (tagName.substring(0, 1).toLowerCase(Locale.ROOT) + tagName.substring(1) + "Api")
                : reducerPath;
        // Put into both the operations map (for template context) and additionalProperties
        // so that Mustache can resolve them at every level of the context stack.
        objs.put("reducerPath",               rPath);
        objs.put("addTagTypes",               addTagTypes);
        objs.put("withReactHooks",            withReactHooks);
        objs.put("importFileExtension",       importFileExtension);
        objs.put("useSingleRequestParameter", useSingleRequestParameter);

        additionalProperties.put("reducerPath",               rPath);
        additionalProperties.put("addTagTypes",               addTagTypes);
        additionalProperties.put("withReactHooks",            withReactHooks);
        additionalProperties.put("importFileExtension",       importFileExtension);
        additionalProperties.put("useSingleRequestParameter", useSingleRequestParameter);

        // tagTypes list — use bare tag name for cache invalidation (e.g. "Pet" not "PetApi")
        Set<String> tagTypeSet = new LinkedHashSet<>();
        tagTypeSet.add(tagName);
        List<String> tagTypeList = new ArrayList<>(tagTypeSet);
        objs.put("tagTypes",              tagTypeList);
        additionalProperties.put("tagTypes", tagTypeList);

        // Schedule per-tag index files
        if (!objs.isEmpty() && !addedApiIndex) {
            addedApiIndex = true;
            supportingFiles.add(new SupportingFile(
                    "apis.index.mustache",
                    apiPackage().replace('.', File.separatorChar),
                    "index.ts"));
        }
        if (!allModels.isEmpty() && !addedModelIndex) {
            addedModelIndex = true;
            supportingFiles.add(new SupportingFile(
                    "models.index.mustache",
                    modelPackage().replace('.', File.separatorChar),
                    "index.ts"));
        }

        this.addOperationModelImportInformation(objs);
        this.updateOperationParameterEnumInformation(objs);
        this.addOperationObjectResponseInformation(objs);

        return objs;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Model post-processing
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        List<ModelMap> models = postProcessModelsEnum(objs).getModels();

        for (ModelMap mo : models) {
            CodegenModel cm = mo.getModel();
            cm.imports = new TreeSet<>(cm.imports);
            for (CodegenProperty var : cm.vars) {
                if (var.isEnum) {
                    var.datatypeWithEnum = var.datatypeWithEnum
                            .replace(var.enumName, cm.classname + var.enumName);
                }
            }
            if (cm.parent != null) {
                for (CodegenProperty var : cm.allVars) {
                    if (var.isEnum) {
                        var.datatypeWithEnum = var.datatypeWithEnum
                                .replace(var.enumName, cm.classname + var.enumName);
                    }
                }
            }
            if (!cm.oneOf.isEmpty()) {
                TreeSet<String> oneOfRefs = new TreeSet<>();
                for (String im : cm.imports) {
                    if (cm.oneOf.contains(im)) oneOfRefs.add(im);
                }
                cm.imports = oneOfRefs;
            }
        }
        return objs;
    }

    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (ModelsMap entry : result.values()) {
            for (ModelMap mo : entry.getModels()) {
                CodegenModel cm = mo.getModel();
                mo.put("hasImports", !cm.imports.isEmpty());
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected void addAdditionPropertiesToCodeGenModel(CodegenModel codegenModel, Schema schema) {
        codegenModel.additionalPropertiesType = getTypeDeclaration(
                ModelUtils.getAdditionalProperties(schema));
        addImport(codegenModel, codegenModel.additionalPropertiesType);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void addOperationModelImportInformation(OperationsMap operations) {
        List<Map<String, String>> imports = operations.getImports();
        for (Map<String, String> im : imports) {
            String[] parts = im.get("import")
                    .replace(modelPackage() + ".", "")
                    .split("( [|&] )|[<>]");
            for (String s : parts) {
                if (needToImport(s)) {
                    im.put("filename", im.get("import"));
                    im.put("className", s);
                }
            }
        }
    }

    private void updateOperationParameterEnumInformation(OperationsMap operations) {
        boolean hasEnum = false;
        for (CodegenOperation op : operations.getOperations().getOperation()) {
            for (CodegenParameter param : op.allParams) {
                if (param.isEnum) {
                    hasEnum = true;
                    param.datatypeWithEnum = param.datatypeWithEnum
                            .replace(param.enumName,
                                    op.operationIdCamelCase + param.enumName);
                }
            }
        }
        operations.put("hasEnums", hasEnum);
    }

    private void addOperationObjectResponseInformation(OperationsMap operations) {
        for (CodegenOperation op : operations.getOperations().getOperation()) {
            if ("object".equals(op.returnType)) {
                op.isMap = true;
                op.returnSimpleType = false;
            }
        }
    }

    private void addExtraReservedWords() {
        this.reservedWords.add("BASE_PATH");
        this.reservedWords.add("BaseAPI");
        this.reservedWords.add("RequiredError");
        this.reservedWords.add("COLLECTION_FORMATS");
        this.reservedWords.add("ConfigurationParameters");
        this.reservedWords.add("Configuration");
        this.reservedWords.add("configuration");
        this.reservedWords.add("HTTPMethod");
        this.reservedWords.add("HTTPHeaders");
        this.reservedWords.add("HTTPQuery");
        this.reservedWords.add("HTTPBody");
        this.reservedWords.add("ModelPropertyNaming");
        this.reservedWords.add("RequestOpts");
        this.reservedWords.add("exists");
        this.reservedWords.add("RequestContext");
        this.reservedWords.add("ResponseContext");
        this.reservedWords.add("Middleware");
        this.reservedWords.add("ApiResponse");
    }

    @Override
    protected String getLicenseNameDefaultValue() {
        return null;
    }
}









