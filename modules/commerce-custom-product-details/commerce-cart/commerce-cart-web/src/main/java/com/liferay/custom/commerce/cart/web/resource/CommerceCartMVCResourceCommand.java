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

import com.liferay.commerce.account.model.CommerceAccount;
import com.liferay.commerce.constants.CommerceWebKeys;
import com.liferay.commerce.context.CommerceContext;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.product.model.CPInstance;
import com.liferay.commerce.product.service.CPInstanceLocalService;
import com.liferay.petra.string.StringUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.JSONPortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCResourceCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCResourceCommand;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.custom.commerce.cart.api.application.CommerceCartService;
import com.liferay.custom.commerce.cart.web.constants.CommerceCartPortletKeys;

import java.util.ArrayList;
import java.util.List;

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
		"mvc.command.name=" + "/update/wholesale/cart"
	},
	service = MVCResourceCommand.class
)
public class CommerceCartMVCResourceCommand extends BaseMVCResourceCommand {

	@Override
	protected void doServeResource(
			ResourceRequest resourceRequest, ResourceResponse resourceResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)resourceRequest.getAttribute(WebKeys.THEME_DISPLAY);

		CommerceContext commerceContext =
			(CommerceContext)resourceRequest.getAttribute(CommerceWebKeys.COMMERCE_CONTEXT);

		CommerceAccount commerceAccount = commerceContext.getCommerceAccount();
		CommerceOrder commerceOrder = commerceContext.getCommerceOrder();

		String cpDefinitionId = ParamUtil.getString(resourceRequest, "cpDefinitionId");
		String cpInstanceIds = ParamUtil.getString(resourceRequest, "cpInstanceIds");
		String quantities = ParamUtil.getString(resourceRequest, "quantities");

		List<String> quantityList = StringUtil.split(quantities, ',');

		List<String> cpInstanceIdList = StringUtil.split(cpInstanceIds, ',');

		List<String> cpInstanceOptions = _getCPInstanceOptions(cpInstanceIdList);

		JSONObject cartData = JSONFactoryUtil.createJSONObject();

		cartData.put("cpDefinitionId", cpDefinitionId);
		cartData.put("cpInstanceIds", cpInstanceIdList);
		cartData.put("groupId", themeDisplay.getScopeGroupId());
		cartData.put("languageId", themeDisplay.getLanguageId());
		cartData.put("options", cpInstanceOptions);
		cartData.put("quantities", quantityList);
		cartData.put("userId", themeDisplay.getUserId());

		if (Validator.isNotNull(commerceAccount)) {
			cartData.put("commerceAccountId", commerceAccount.getCommerceAccountId());
		}
		else {
			cartData.put("commerceAccountId", 0);
		}

		if (Validator.isNotNull(commerceOrder)) {
			cartData.put("orderId", commerceOrder.getCommerceOrderId());
		}
		else {
			cartData.put("orderId", 0);
		}

		String response = _commerceCartService.updateOrderItem(
			themeDisplay.getCompanyId(), _portal.getHttpServletRequest(resourceRequest), cartData.toString());

		JSONPortletResponseUtil.writeJSON(resourceRequest, resourceResponse, response);
	}

	private List<String> _getCPInstanceOptions(List<String> cpInstanceIds) {
		List<String> options = new ArrayList<>();

		String errorCPInstanceId = null;

		try {
			for (String cpInstanceId : cpInstanceIds) {
				errorCPInstanceId = cpInstanceId;

				CPInstance cpInstance = _cpInstanceLocalService.getCPInstance(GetterUtil.getLong(cpInstanceId));

				String json = cpInstance.getJson();

				options.add(json);
			}
		}
		catch (PortalException pe) {
			_log.error("The cpInstance with cpInstanceId" + "[" + errorCPInstanceId + "] could not be found.", pe);
		}

		return options;
	}

	private static final Log _log = LogFactoryUtil.getLog(CommerceCartMVCResourceCommand.class);

	@Reference
	private CommerceCartService _commerceCartService;

	@Reference
	private CPInstanceLocalService _cpInstanceLocalService;

	@Reference
	private Portal _portal;

}