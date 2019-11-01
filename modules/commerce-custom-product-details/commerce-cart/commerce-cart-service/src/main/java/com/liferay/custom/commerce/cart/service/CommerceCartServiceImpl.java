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

package com.liferay.custom.commerce.cart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.liferay.commerce.account.exception.NoSuchAccountException;
import com.liferay.commerce.account.model.CommerceAccount;
import com.liferay.commerce.constants.CPDefinitionInventoryConstants;
import com.liferay.commerce.context.CommerceContext;
import com.liferay.commerce.context.CommerceContextFactory;
import com.liferay.commerce.currency.model.CommerceCurrency;
import com.liferay.commerce.exception.CommerceOrderValidatorException;
import com.liferay.commerce.inventory.CPDefinitionInventoryEngine;
import com.liferay.commerce.inventory.CPDefinitionInventoryEngineRegistry;
import com.liferay.commerce.model.CPDefinitionInventory;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.model.CommerceOrderItem;
import com.liferay.commerce.order.CommerceOrderHttpHelper;
import com.liferay.commerce.order.CommerceOrderValidatorResult;
import com.liferay.commerce.product.model.CPInstance;
import com.liferay.commerce.product.service.CPInstanceLocalService;
import com.liferay.commerce.product.service.CommerceChannelLocalService;
import com.liferay.commerce.service.CPDefinitionInventoryLocalService;
import com.liferay.commerce.service.CommerceOrderItemService;
import com.liferay.commerce.service.CommerceOrderService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.ResourceBundleUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.custom.commerce.cart.api.application.CommerceCartService;
import com.liferay.custom.commerce.cart.api.application.model.Cart;
import com.liferay.custom.commerce.cart.service.util.CommerceCartResourceUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

import javax.portlet.PortletURL;

import javax.servlet.http.HttpServletRequest;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Jared Domio
 */
@Component(
	immediate = true,
	service = CommerceCartService.class
)
public class CommerceCartServiceImpl implements CommerceCartService {

	@Override
	public String updateOrderItem(long companyId, HttpServletRequest httpServletRequest, String cartData)
		throws JsonProcessingException {

		Cart cart;

		try {
			JSONObject cartObject = JSONFactoryUtil.createJSONObject(cartData);

			long groupId = cartObject.getLong("groupId");
			long commerceAccountId = cartObject.getLong("commerceAccountId");
			long orderId = cartObject.getLong("orderId");
			long userId = cartObject.getLong("userId");
			long cpDefinitionId = cartObject.getLong("cpDefinitionId");
			JSONArray cpInstanceIds = cartObject.getJSONArray("cpInstanceIds");
			JSONArray options = cartObject.getJSONArray("options");
			JSONArray quantities = cartObject.getJSONArray("quantities");
			String languageId = cartObject.getString("languageId");

			Locale locale = LocaleUtil.fromLanguageId(languageId);

			long minQuantity = _getMinQuantity(cpDefinitionId);

			if (cpInstanceIds.length() <= 0) {
				List<CommerceOrderValidatorResult> commerceOrderValidatorResults = new ArrayList<>();

				commerceOrderValidatorResults.add(
					new CommerceOrderValidatorResult(
						false,
						_getLocalizedMessage(
							locale, "the-minimum-quantity-is-x",
							new Object[] {String.valueOf(minQuantity)})));

				throw new CommerceOrderValidatorException(commerceOrderValidatorResults);
			}

			CommerceContext commerceContext = _commerceContextFactory.create(
				companyId, groupId, userId, orderId, commerceAccountId);

			CommerceOrder commerceOrder = _commerceOrderService.fetchCommerceOrder(orderId);

			if (Objects.isNull(commerceOrder)) {
				commerceOrder = _addCommerceOrder(groupId, userId, commerceContext);
			}

			commerceContext = _commerceContextFactory.create(
				companyId, groupId, userId, commerceOrder.getCommerceOrderId(), commerceAccountId);

			PortletURL portletURL = _commerceOrderHttpHelper.getCommerceCartPortletURL(
				groupId, httpServletRequest, commerceOrder);

			ServiceContext serviceContext = new ServiceContext();

			serviceContext.setScopeGroupId(commerceOrder.getGroupId());
			serviceContext.setUserId(userId);

			_upsertCommerceOrderItems(
				commerceOrder.getCommerceOrderId(), cpInstanceIds, quantities, options, commerceContext,
				serviceContext);

			cart = _commerceCartResourceUtil.getCart(
				commerceOrder.getCommerceOrderId(), portletURL.toString(), locale, commerceContext);
		}
		catch (Exception e) {
			List<CommerceOrderValidatorResult> commerceOrderValidatorResults;

			if (e instanceof CommerceOrderValidatorException) {
				commerceOrderValidatorResults =
					((CommerceOrderValidatorException)e).getCommerceOrderValidatorResults();

				cart = new Cart(_getCommerceOrderValidatorResultsMessages(commerceOrderValidatorResults));
			}
			else {
				if (!(e instanceof NoSuchAccountException)) {
					_log.error(e, e);
				}

				cart = new Cart(StringUtil.split(e.getLocalizedMessage()));
			}
		}

		return _OBJECT_MAPPER.writeValueAsString(cart);
	}

	private CommerceOrder _addCommerceOrder(
			long groupId, long userId, CommerceContext commerceContext)
		throws PortalException {

		long commerceCurrencyId = 0;

		CommerceCurrency commerceCurrency = commerceContext.getCommerceCurrency();

		if (!Objects.isNull(commerceCurrency)) {
			commerceCurrencyId = commerceCurrency.getCommerceCurrencyId();
		}

		CommerceAccount commerceAccount = commerceContext.getCommerceAccount();

		if (Objects.isNull(commerceAccount)) {
			throw new NoSuchAccountException("No Account selected");
		}

		long commerceChannelGroupId =
			_commerceChannelLocalService.getCommerceChannelGroupIdBySiteGroupId(groupId);

		CommerceOrder commerceOrder = _commerceOrderService.addCommerceOrder(
			userId, commerceChannelGroupId, commerceAccount.getCommerceAccountId(), commerceCurrencyId);

		return commerceOrder;
	}

	private String[] _getCommerceOrderValidatorResultsMessages(
		List<CommerceOrderValidatorResult> commerceOrderValidatorResults) {

		String[] errorMessages = new String[0];

		for (CommerceOrderValidatorResult commerceOrderValidatorResult : commerceOrderValidatorResults) {
			if (commerceOrderValidatorResult.hasMessageResult()) {
				errorMessages = ArrayUtil.append(
					errorMessages,
					commerceOrderValidatorResult.getLocalizedMessage());
			}
		}

		return errorMessages;
	}

	private String _getLocalizedMessage(
		Locale locale, String key, Object[] arguments) {

		ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", locale, getClass());

		if (Objects.isNull(arguments)) {
			return LanguageUtil.get(resourceBundle, key);
		}

		return LanguageUtil.format(resourceBundle, key, arguments);
	}

	private long _getMinQuantity(long cpDefinitionId) {
		try {
			CPDefinitionInventory cpDefinitionInventory =
				_cpDefinitionInventoryLocalService.fetchCPDefinitionInventoryByCPDefinitionId(cpDefinitionId);

			CPDefinitionInventoryEngine cpDefinitionInventoryEngine =
				_cpDefinitionInventoryEngineRegistry.getCPDefinitionInventoryEngine(cpDefinitionInventory);

			List<CPInstance> cpInstances = _cpInstanceLocalService.getCPDefinitionInstances(cpDefinitionId);

			return cpDefinitionInventoryEngine.getMinOrderQuantity(cpInstances.get(0));
		}
		catch (Exception e) {
			_log.error(e, e);

			return CPDefinitionInventoryConstants.DEFAULT_MIN_ORDER_QUANTITY;
		}
	}

	private List<CommerceOrderItem> _upsertCommerceOrderItems(
			long orderId, JSONArray cpInstanceIds, JSONArray quantities, JSONArray options,
			CommerceContext commerceContext, ServiceContext serviceContext)
		throws PortalException {

		List<CommerceOrderItem> commerceOrderItems = new ArrayList<>();

		for (int i = 0; i < cpInstanceIds.length(); i++) {
			long cpInstanceId = cpInstanceIds.getLong(i);
			int quantity = quantities.getInt(i);
			String option = options.getString(i);

			CommerceOrderItem commerceOrderItem = _commerceOrderItemService.upsertCommerceOrderItem(
				orderId, cpInstanceId, quantity, 0, option, commerceContext, serviceContext);

			commerceOrderItems.add(commerceOrderItem);
		}

		return commerceOrderItems;
	}

	private static final ObjectMapper _OBJECT_MAPPER = new ObjectMapper() {
		{
			configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
			disable(SerializationFeature.INDENT_OUTPUT);
		}
	};

	private static final Log _log = LogFactoryUtil.getLog(CommerceCartServiceImpl.class);

	@Reference
	private CommerceCartResourceUtil _commerceCartResourceUtil;

	@Reference
	private CommerceChannelLocalService _commerceChannelLocalService;

	@Reference
	private CommerceContextFactory _commerceContextFactory;

	@Reference
	private CommerceOrderHttpHelper _commerceOrderHttpHelper;

	@Reference
	private CommerceOrderItemService _commerceOrderItemService;

	@Reference
	private CommerceOrderService _commerceOrderService;

	@Reference
	private CPDefinitionInventoryEngineRegistry _cpDefinitionInventoryEngineRegistry;

	@Reference
	private CPDefinitionInventoryLocalService _cpDefinitionInventoryLocalService;

	@Reference
	private CPInstanceLocalService _cpInstanceLocalService;

}