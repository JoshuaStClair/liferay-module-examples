import Component from 'metal-component';
import Soy from 'metal-soy';

import templates from './table.soy';

/**
 * Table Component
 */
class Table extends Component {}

// Register component
Soy.register(Table, templates, 'render');

if (!window.DDMTable) {
	window.DDMTable = {

	};
}

window.DDMTable.render = Table;

export default Table;