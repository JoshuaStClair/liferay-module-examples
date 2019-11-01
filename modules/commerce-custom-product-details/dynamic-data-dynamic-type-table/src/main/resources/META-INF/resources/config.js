;(function() {
	AUI().applyConfig(
		{
			groups: {
				'field-table': {
					base: MODULE_PATH + '/',
					combine: Liferay.AUI.getCombine(),
					filter: Liferay.AUI.getFilterConfig(),
					modules: {
						'dynamic-data-dynamic-type-table': {
							condition: {
								trigger: 'liferay-ddm-form-renderer'
							},
							path: 'table_field.js',
							requires: [
								'liferay-ddm-form-renderer-field'
							]
						}
					},
					root: MODULE_PATH + '/'
				}
			}
		}
	);
})();