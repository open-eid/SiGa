requirejs.config({
    paths: {
        "Vue": "/webjars/vue/dist/vue.min",
        "vue": "https://cdn.jsdelivr.net/npm/require-vuejs@1.1.3/dist/require-vuejs.min",
        "vue-dropzone": "/webjars/vue2-dropzone/dist/vue2Dropzone",
        "vue-bootstrap": "/webjars/bootstrap-vue/dist/bootstrap-vue.min",
        "axios": "/webjars/axios/0.19.0/dist/axios"
    },
    shim: {
        "Vue": {"exports": "Vue"}
    }
});

require(["Vue", "vue-bootstrap", "vue!components/container-processing.vue"], function (Vue, VueBootstrap) {
    Vue.use(VueBootstrap);
    new Vue({
        el: "#siga-app"
    });

    Vue.directive('highlightjs', {
        deep: true,
        bind: function (el, binding) {
            let targets = el.querySelectorAll('code')
            targets.forEach((target) => {
                if (binding.value) {
                    target.textContent = binding.value
                }
                hljs.highlightBlock(target)
            })
        },
        componentUpdated: function (el, binding) {
            let targets = el.querySelectorAll('code')
            targets.forEach((target) => {
                if (binding.value) {
                    target.textContent = binding.value
                    hljs.highlightBlock(target)
                }
            })
        }
    })
});