<template>
    <b-container>
        <b-row>
            <b-col lg>
                <b-img id="eu-brand" right src="/img/eu-logo.svg" alt="EU logo"></b-img>
            </b-col>
        </b-row>
        <b-row>
            <b-col lg>
                <div class="card">
                    <h5 class="card-header">Container upload and conversion to hashcode format</h5>
                    <div class="card-body">
                        <b-form-group>
                            <b-form-radio v-model="containerConversionType" value="convertContainer">Convert ASIC container to HASHCODE container</b-form-radio>
                            <b-form-radio v-model="containerConversionType" value="createContainer">Create ASIC container from files</b-form-radio>
                            <b-form-radio v-model="containerConversionType" value="createHashcodeContainer" v-on:input="onChangeConversionType">Create HASHCODE container from files</b-form-radio>
                        </b-form-group>
                        <b-container>
                            <b-row>
                                <b-col>
                                    <h6 v-if="!containerConverted && containerConversionType === 'convertContainer'">Upload container</h6>
                                    <h6 v-if="!containerConverted && containerConversionType === 'createHashcodeContainer'">Upload any type of files to create new hashcode container</h6>
                                    <h6 v-if="!containerConverted && containerConversionType === 'createContainer'">Upload any type of files to create new container</h6>
                                </b-col>
                                <b-col cols="auto" align-self="end">
                                    <b-button v-if="!containerConverted && containerConversionType != 'convertContainer'" v-b-toggle.collapse-ms variant="primary" size="sm" v-on:click="onCreateContainer">Upload files</b-button>
                                </b-col>
                            </b-row>
                        </b-container>
                        <b-alert :show="dropzoneErrorCountdown" @dismissed="dropzoneErrorCountdown=0" dismissible fade variant="warning">
                            {{ dropzoneError }}
                        </b-alert>
                        <vue-dropzone id="filedropzone" v-if="!containerConverted" ref="fileDropzone" :options="dropzoneOptions" v-on:vdropzone-success-multiple="onUploadContainerSuccess" v-on:vdropzone-error="onDropzoneError"></vue-dropzone>
                        <b-button target="_blank" v-if="containerConverted && containerConversionType != 'createContainer'" v-bind:href="downloadHashcodeUrl" size="sm">Download unsigned hashcode container</b-button>
                    </div>
                </div>
            </b-col>
        </b-row>
        <b-row v-if="containerConverted">
            <b-col lg>
                <div class="card">
                    <h5 class="card-header">Sign container</h5>
                    <div class="card-body">
                        <b-collapse visible>
                            <b-container>
                                <b-row>
                                    <b-col>
                                        <b-form @submit="onMobileSign">
                                            <b-form-group label="Person identifier code:" label-for="input-1" label-cols-md="3">
                                                <b-form-input id="input-1" v-model="mobileSigningForm.personIdentifier" type="text" required placeholder="Person identifier code"></b-form-input>
                                            </b-form-group>
                                            <b-form-group label="Phone nr:" label-for="input-2" label-cols-md="3">
                                                <b-form-input id="input-2" v-model="mobileSigningForm.phoneNr" type="text" required placeholder="Phone nr"></b-form-input>
                                            </b-form-group>
                                            <b-form-group label="Select country:" label-for="input-3" label-cols-md="3">
                                                <b-form-select id="input-3" v-model="mobileSigningForm.country" :options="countryOptions"></b-form-select>
                                            </b-form-group>
                                            <b-form-group label-cols-md="12">
                                                <b-button type="submit" v-b-toggle.collapse-ms variant="primary" size="sm">Start mobile signing</b-button>
                                            </b-form-group>
                                        </b-form>
                                    </b-col>
                                </b-row>
                            </b-container>
                        </b-collapse>
                        <div class="process-callout process-start" v-bind:class="{ 'process-default': item.status === 'PROCESSING', 'process-highlight': item.status === 'VALIDATION', 'process-highlight': item.status === 'CHALLENGE', 'process-result': item.status === 'RESULT', 'process-error': item.status === 'ERROR'}" v-for="(item, index) in processingSteps">
                            <h6>
                                <b-badge variant="primary">{{index}}</b-badge>
                                {{item.requestMethod}} {{item.apiEndpoint}}
                            </h6>
                            <b-container>
                                <b-row>
                                    <b-col>
                                        <b-alert v-if="item.errorMessage !== null" show variant="danger">{{item.errorMessage}}</b-alert>
                                        <b-button v-if="item.containerReadyForDownload && containerConversionType === 'createHashcodeContainer'" v-bind:href="downloadHashcodeUrl" size="sm" variant="link">Download signed hashcode container</b-button>
                                        <b-button v-if="item.containerReadyForDownload" v-bind:href="downloadRegularUrl" size="sm" variant="link">Download signed regular container</b-button>
                                    </b-col>
                                    <b-badge v-if="item.apiRequestObject !== null" v-b-toggle="'process-request-' + index" href="#" variant="success">Request</b-badge>
                                    <b-badge v-b-toggle="'process-response-' + index" href="#" variant="warning">Response</b-badge>
                                </b-row>
                                <b-collapse :id="'process-request-' + index">
                                    <pre class="process-details" v-highlightjs><code class="json">{{ item.apiRequestObject }}</code></pre>
                                </b-collapse>
                                <b-collapse :id="'process-response-' + index">
                                    <pre class="process-details" v-highlightjs><code class="json">{{ item.apiResponseObject }}</code></pre>
                                </b-collapse>
                            </b-container>
                        </div>
                    </div>
                </div>
            </b-col>
        </b-row>
    </b-container>
</template>

<script>
    define(["Vue", "vue-dropzone", "axios"], function (Vue, VueDropzone, axios) {
        return Vue.component("siga-container-processing", {
            template: template,
            components: {
                vueDropzone: VueDropzone
            },
            data: function () {
                return {
                    processingSteps: [],
                    fileId: null,
                    containerConversionType: 'convertContainer',
                    containerConverted: false,
                    downloadHashcodeUrl: null,
                    downloadRegularUrl: null,
                    mobileSigningForm: {
                        fileId: null,
                        personIdentifier: '60001019906',
                        phoneNr: '+37200000766',
                        country: 'EE',
                        containerType: 'HASHCODE'
                    },
                    countryOptions: [
                        {value: 'EE', text: 'EE'},
                        {value: 'LV', text: 'LV'},
                        {value: 'LT', text: 'LT'}
                    ],
                    dropzoneErrorCountdown: 0,
                    dropzoneError: null,
                    dropzoneOptions: {
                        url: '/convert-container',
                        thumbnailWidth: 150,
                        acceptedFiles: ".asice, .bdoc",
                        maxFiles: 1,
                        maxFilesize: 25,
                        previewTemplate: '<progress class="progress progress-info progress-striped" id="file-progress" value="0" max="100"></progress>',
                        dictDefaultMessage: 'Drop files here or click to browse for upload file.',
                        uploadMultiple: true
                    }
                };
            },
            methods: {
                onCreateContainer: function () {
                    this.$refs.fileDropzone.processQueue();
                },
                onUploadContainerSuccess: function (files, response) {
                    console.log('File upload id: ' + response.id);
                    this.$refs.fileDropzone.removeAllFiles();
                    this.$data.downloadHashcodeUrl = '/download/hashcode/' + response.id;
                    this.$data.downloadRegularUrl = '/download/regular/' + this.$data.mobileSigningForm.containerType + '/' + response.id;
                    this.$data.mobileSigningForm.fileId = response.id;
                    this.$data.containerConverted = true;
                    let processingSteps = this.$data.processingSteps;
                    this.stompClient.subscribe('/progress/' + response.id, function (message) {
                        processingSteps.push(JSON.parse(message.body));
                    });
                },
                onMobileSign: function (evt) {
                    evt.preventDefault()
                    axios.post('/mobile-signing', this.$data.mobileSigningForm);
                },
                onDropzoneError: function (file, message, xhr) {
                    this.$refs.fileDropzone.removeFile(file)
                    this.$data.dropzoneError = message;
                    this.$data.dropzoneErrorCountdown = 5;
                },
                onChangeConversionType: function () {
                    this.$refs.fileDropzone.removeAllFiles();
                    if (this.$data.containerConversionType === 'convertContainer') {
                        this.$data.mobileSigningForm.containerCreated = false;
                        this.$data.mobileSigningForm.containerType = "HASHCODE";
                        this.$refs.fileDropzone.setOption('url', '/convert-container');
                        this.$refs.fileDropzone.setOption('maxFiles', '1');
                        this.$refs.fileDropzone.setOption('acceptedFiles', '.asice, .bdoc');
                        this.$refs.fileDropzone.setOption('autoProcessQueue', true);
                    } else if(this.$data.containerConversionType === 'createHashcodeContainer'){
                        this.$data.mobileSigningForm.containerCreated = true;
                        this.$data.mobileSigningForm.containerType = "HASHCODE";
                        this.$refs.fileDropzone.setOption('url', '/create-hashcode-container');
                        this.$refs.fileDropzone.setOption('maxFiles', '10');
                        this.$refs.fileDropzone.setOption('acceptedFiles', null);
                        this.$refs.fileDropzone.setOption('autoProcessQueue', false);
                    } else {
                        this.$data.mobileSigningForm.containerCreated = true;
                        this.$data.mobileSigningForm.containerType = "ASIC";
                        this.$refs.fileDropzone.setOption('url', '/create-container');
                        this.$refs.fileDropzone.setOption('maxFiles', '10');
                        this.$refs.fileDropzone.setOption('acceptedFiles', null);
                        this.$refs.fileDropzone.setOption('autoProcessQueue', false);
                    }
                }
            },
            mounted: function () {
                this.stompClient = Stomp.over(new WebSocket('ws://localhost:8081/stomp'));
                this.stompClient.connect();
                this.$data.showMobileSigning = false;
            },
        });
    });
</script>
