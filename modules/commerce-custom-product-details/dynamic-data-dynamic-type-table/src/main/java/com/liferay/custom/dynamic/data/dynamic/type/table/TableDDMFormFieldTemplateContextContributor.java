/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.custom.dynamic.data.dynamic.type.table;

import com.liferay.commerce.constants.CPDefinitionInventoryConstants;
import com.liferay.commerce.constants.CommerceWebKeys;
import com.liferay.commerce.context.CommerceContext;
import com.liferay.commerce.currency.model.CommerceCurrency;
import com.liferay.commerce.inventory.CPDefinitionInventoryEngine;
import com.liferay.commerce.inventory.CPDefinitionInventoryEngineRegistry;
import com.liferay.commerce.inventory.engine.CommerceInventoryEngine;
import com.liferay.commerce.model.CPDefinitionInventory;
import com.liferay.commerce.product.catalog.CPCatalogEntry;
import com.liferay.commerce.product.catalog.CPSku;
import com.liferay.commerce.product.content.util.CPContentHelper;
import com.liferay.commerce.product.model.CPDefinition;
import com.liferay.commerce.product.model.CPInstance;
import com.liferay.commerce.product.service.CPInstanceLocalService;
import com.liferay.commerce.service.CPDefinitionInventoryLocalService;
import com.liferay.dynamic.data.mapping.form.field.type.DDMFormFieldOptionsFactory;
import com.liferay.dynamic.data.mapping.form.field.type.DDMFormFieldTemplateContextContributor;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.dynamic.data.mapping.model.DDMFormFieldOptions;
import com.liferay.dynamic.data.mapping.model.LocalizedValue;
import com.liferay.dynamic.data.mapping.render.DDMFormFieldRenderingContext;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.HtmlUtil;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Ricky Pan
 */
@Component(
	immediate = true, property = "ddm.form.field.type.name=table",
	service = {
		DDMFormFieldTemplateContextContributor.class,
		TableDDMFormFieldTemplateContextContributor.class
	}
)
public class TableDDMFormFieldTemplateContextContributor implements DDMFormFieldTemplateContextContributor {

	@Override
	public Map<String, Object> getParameters(
		DDMFormField ddmFormField,
		DDMFormFieldRenderingContext ddmFormFieldRenderingContext) {

		Map<String, Object> parameters = new HashMap<>();

		parameters.put("options", getOptions(ddmFormField, ddmFormFieldRenderingContext));

		return parameters;
	}

	protected String getCPDefinitionId(CPInstance cpInstance) {
		try {
			CPDefinition cpDefinition = cpInstance.getCPDefinition();

			return String.valueOf(cpDefinition.getCPDefinitionId());
		}
		catch (PortalException pe) {
			_log.error(pe, pe);

			return StringPool.BLANK;
		}
	}

	protected CPInstance getCPInstance(List<CPSku> cpSkus, String optionValue) {
		if (Objects.isNull(cpSkus)) {
			return null;
		}

		for (CPSku cpSku : cpSkus) {
			long cpInstanceId = cpSku.getCPInstanceId();

			CPInstance cpInstance = cpInstanceLocalService.fetchCPInstance(cpInstanceId);

			if (Objects.isNull(cpInstance)) {
				continue;
			}

			String optionsJSON = cpInstance.getJson();

			if (optionsJSON.contains(optionValue)) {
				return cpInstance;
			}
		}

		return null;
	}

	protected String getMaxQuantity(CPInstance cpInstance) {
		try {
			CPDefinitionInventory cpDefinitionInventory =
				cpDefinitionInventoryLocalService.fetchCPDefinitionInventoryByCPDefinitionId(
					cpInstance.getCPDefinitionId());

			CPDefinitionInventoryEngine cpDefinitionInventoryEngine =
				cpDefinitionInventoryEngineRegistry.getCPDefinitionInventoryEngine(cpDefinitionInventory);

			int maxQuantity = cpDefinitionInventoryEngine.getMaxOrderQuantity(cpInstance);

			return String.valueOf(maxQuantity);
		}
		catch (Exception e) {
			_log.error(e, e);

			return String.valueOf(CPDefinitionInventoryConstants.DEFAULT_MAX_ORDER_QUANTITY);
		}
	}

	protected List<Object> getOptions(
		DDMFormField ddmFormField,
		DDMFormFieldRenderingContext ddmFormFieldRenderingContext) {

		List<Object> options = new ArrayList<>();

		DDMFormFieldOptions ddmFormFieldOptions =
			ddmFormFieldOptionsFactory.create(ddmFormField, ddmFormFieldRenderingContext);

		HttpServletRequest request = ddmFormFieldRenderingContext.getHttpServletRequest();

		CommerceContext commerceContext =
			(CommerceContext)request.getAttribute(CommerceWebKeys.COMMERCE_CONTEXT);

		CPContentHelper cpContentHelper = (CPContentHelper)request.getAttribute("CP_CONTENT_HELPER");

		List<CPSku> cpSkus = Collections.emptyList();

		if (!Objects.isNull(cpContentHelper)) {
			CPCatalogEntry cpCatalogEntry = cpContentHelper.getCPCatalogEntry(request);

			cpSkus = cpCatalogEntry.getCPSkus();
		}

		for (String optionValue : ddmFormFieldOptions.getOptionsValues()) {
			Map<String, String> optionMap = new HashMap<>();

			LocalizedValue optionLabel = ddmFormFieldOptions.getOptionLabels(optionValue);

			String optionLabelString = optionLabel.getString(ddmFormFieldRenderingContext.getLocale());

			if (ddmFormFieldRenderingContext.isViewMode()) {
				optionLabelString = HtmlUtil.extractText(optionLabelString);
			}

			optionMap.put("label", optionLabelString);

			optionMap.put("value", optionValue);

			CPInstance cpInstance = getCPInstance(cpSkus, optionValue);

			String cpInstanceId = StringPool.BLANK;

			String cpDefinitionId = StringPool.BLANK;

			String price = StringPool.BLANK;

			String quantity = "0";

			if (Objects.nonNull(cpInstance)) {
				cpInstanceId = String.valueOf(cpInstance.getCPInstanceId());

				cpDefinitionId = getCPDefinitionId(cpInstance);

				price = getPrice(cpInstance, commerceContext);

				try {
					quantity = getQuantity(cpInstance, commerceContext.getCommerceChannelGroupId());
				}
				catch (PortalException pe) {
					_log.error(pe, pe);
				}
			}

			optionMap.put("cpInstanceId", cpInstanceId);

			optionMap.put("cpDefinitionId", cpDefinitionId);

			optionMap.put("maxQuantity", getMaxQuantity(cpInstance));

			optionMap.put("price", price);

			optionMap.put("quantity", quantity);

			options.add(optionMap);
		}

		return options;
	}

	protected String getPrice(CPInstance cpInstance, CommerceContext commerceContext) {
		try {
			CommerceCurrency commerceCurrency = commerceContext.getCommerceCurrency();

			BigDecimal price = commerceCurrency.round(cpInstance.getPrice());

			return price.toString();
		}
		catch (PortalException pe) {
			_log.error(
				"Could not retrieve CommerceCurrency to get the price of CPInstance with cpInstanceId [" +
					cpInstance.getCPInstanceId() + "]",
				pe);

			return StringPool.BLANK;
		}
	}

	protected String getQuantity(CPInstance cpInstance, long groupId) {
		try {
			long availableQuantity = commerceInventoryEngine.getStockQuantity(
				cpInstance.getCompanyId(), groupId, cpInstance.getSku());

			return String.valueOf(availableQuantity);
		}
		catch (PortalException pe) {
			_log.error(pe, pe);

			return "0";
		}
	}

	@Reference
	protected CommerceInventoryEngine commerceInventoryEngine;

	@Reference
	protected CPDefinitionInventoryEngineRegistry cpDefinitionInventoryEngineRegistry;

	@Reference
	protected CPDefinitionInventoryLocalService cpDefinitionInventoryLocalService;

	@Reference
	protected CPInstanceLocalService cpInstanceLocalService;

	@Reference
	protected DDMFormFieldOptionsFactory ddmFormFieldOptionsFactory;

	private static final Log _log = LogFactoryUtil.getLog(TableDDMFormFieldTemplateContextContributor.class);

}