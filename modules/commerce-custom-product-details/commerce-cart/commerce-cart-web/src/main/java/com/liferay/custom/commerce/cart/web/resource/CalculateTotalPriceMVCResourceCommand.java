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

package com.liferay.custom.commerce.cart.web.command.resource;

import com.liferay.commerce.constants.CPDefinitionInventoryConstants;
import com.liferay.commerce.constants.CommerceWebKeys;
import com.liferay.commerce.context.CommerceContext;
import com.liferay.commerce.currency.model.CommerceCurrency;
import com.liferay.commerce.inventory.CPDefinitionInventoryEngine;
import com.liferay.commerce.inventory.CPDefinitionInventoryEngineRegistry;
import com.liferay.commerce.model.CPDefinitionInventory;
import com.liferay.commerce.product.model.CPInstance;
import com.liferay.commerce.product.service.CPInstanceLocalService;
import com.liferay.commerce.service.CPDefinitionInventoryLocalService;
import com.liferay.petra.string.StringPool;
import com.liferay.petra.string.StringUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.JSONPortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCResourceCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCResourceCommand;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.ResourceBundleUtil;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.custom.commerce.cart.web.constants.CommerceCartPortletKeys;

import java.math.BigDecimal;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Jared Domio
 */
@Component(
	immediate = true,
	property = {
		"javax.portlet.name=" + CommerceCartPortletKeys.COMMERCE_CART,
		"mvc.command.name=" + "/calculate/total/price"
	},
	service = MVCResourceCommand.class
)
public class CalculateTotalPriceMVCResourceCommand extends BaseMVCResourceCommand {

	@Override
	protected void doServeResource(
			ResourceRequest resourceRequest, ResourceResponse resourceResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)resourceRequest.getAttribute(WebKeys.THEME_DISPLAY);

		CommerceContext commerceContext =
			(CommerceContext)resourceRequest.getAttribute(CommerceWebKeys.COMMERCE_CONTEXT);

		String cpInstanceIds = ParamUtil.getString(resourceRequest, "cpInstanceIds");
		String quantities = ParamUtil.getString(resourceRequest, "quantities");

		List<String> quantityList = StringUtil.split(quantities, ',');

		List<String> cpInstanceIdList = StringUtil.split(cpInstanceIds, ',');

		JSONObject response = JSONFactoryUtil.createJSONObject();

		response.put(
			"errorMessage",
			_getErrorMessage(cpInstanceIdList, quantityList, themeDisplay.getLocale()));

		response.put("totalPrice", _getTotal(quantityList, cpInstanceIdList, commerceContext));

		JSONPortletResponseUtil.writeJSON(resourceRequest, resourceResponse, response);
	}

	private String _getErrorMessage(List<String> cpInstanceIdList, List<String> quantityList, Locale locale) {
		try {
			for (int i = 0; i < cpInstanceIdList.size(); i++) {
				long cpInstanceId = GetterUtil.getLong(cpInstanceIdList.get(i));
				long quantity = GetterUtil.getLong(quantityList.get(i));

				// Only quantities that have a non zero value are retrieved from the DOM. Here we check if the quantity
				// is too large. If the quantity is too large the GetterUtil defaults the value to 0.

				if (GetterUtil.getLong(quantity) <= 0) {
					CPInstance cpInstance = _cpInstanceLocalService.getCPInstance(cpInstanceId);

					return _getLocalizedMessage(
						locale, "the-maximum-quantity-is-x", new Object[] {_getMaxQuantity(cpInstance)});
				}
			}
		}
		catch (PortalException pe) {
			_log.error("Unable to retrieve cpInstance for errorMessage", pe);
		}

		return StringPool.BLANK;
	}

	private String _getLocalizedMessage(
		Locale locale, String key, Object[] arguments) {

		ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", locale, getClass());

		if (Objects.isNull(arguments)) {
			return LanguageUtil.get(resourceBundle, key);
		}

		return LanguageUtil.format(resourceBundle, key, arguments);
	}

	private String _getMaxQuantity(CPInstance cpInstance) {
		try {
			CPDefinitionInventory cpDefinitionInventory =
				_cpDefinitionInventoryLocalService.fetchCPDefinitionInventoryByCPDefinitionId(
					cpInstance.getCPDefinitionId());

			CPDefinitionInventoryEngine cpDefinitionInventoryEngine =
				_cpDefinitionInventoryEngineRegistry.getCPDefinitionInventoryEngine(cpDefinitionInventory);

			int maxQuantity = cpDefinitionInventoryEngine.getMaxOrderQuantity(cpInstance);

			return String.valueOf(maxQuantity);
		}
		catch (Exception e) {
			_log.error(e, e);

			return String.valueOf(CPDefinitionInventoryConstants.DEFAULT_MAX_ORDER_QUANTITY);
		}
	}

	private String _getTotal(
		List<String> quantityList, List<String> cpInstanceIdList, CommerceContext commerceContext) {

		try {
			CommerceCurrency commerceCurrency = commerceContext.getCommerceCurrency();
			BigDecimal total = BigDecimal.ZERO;

			for (int i = 0; i < cpInstanceIdList.size(); i++) {
				long cpInstanceId = GetterUtil.getLong(cpInstanceIdList.get(i));
				long quantity = GetterUtil.getLong(quantityList.get(i));

				CPInstance cpInstance = _cpInstanceLocalService.getCPInstance(cpInstanceId);

				BigDecimal price = cpInstance.getPrice();

				price = price.multiply(BigDecimal.valueOf(quantity));

				total = total.add(price);
			}

			BigDecimal roundedTotal = commerceCurrency.round(total);

			return roundedTotal.toString();
		}
		catch (PortalException pe) {
			_log.error("Unable to calculate total price", pe);

			return StringPool.BLANK;
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(CommerceCartMVCResourceCommand.class);

	@Reference
	private CPDefinitionInventoryEngineRegistry _cpDefinitionInventoryEngineRegistry;

	@Reference
	private CPDefinitionInventoryLocalService _cpDefinitionInventoryLocalService;

	@Reference
	private CPInstanceLocalService _cpInstanceLocalService;

	@Reference
	private JSONFactory _jsonFactory;

}