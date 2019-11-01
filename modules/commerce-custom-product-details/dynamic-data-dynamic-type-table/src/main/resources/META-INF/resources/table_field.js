AUI.add(
	'dynamic-data-dynamic-type-table',
	function(A) {
		var TableField = A.Component.create(
			{
				ATTRS: {
					type: {
						value: 'table'
					}
				},

				EXTENDS: Liferay.DDM.Renderer.Field,

				NAME: 'dynamic-data-dynamic-type-table',

				prototype: {
				}
			}
		);

		Liferay.namespace('DDM.Field').Table = TableField;
	},
	'',
	{
		requires: ['liferay-ddm-form-renderer-field']
	}
);