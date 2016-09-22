/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.authentication;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.Settings;
import org.sonar.api.server.authentication.Display;
import org.sonar.api.server.authentication.IdentityProvider;
import org.sonar.api.server.authentication.UserIdentity;
import org.sonar.db.user.UserDto;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.trimToNull;
import static org.sonar.api.CoreProperties.CATEGORY_SECURITY;
import static org.sonar.api.PropertyType.BOOLEAN;
import static org.sonar.server.user.UserUpdater.SQ_AUTHORITY;

public class SsoAuthenticator {

  private static final Splitter COMA_SPLITTER = Splitter.on(",").trimResults().omitEmptyStrings();

  private static final String ENABLE_PARAM = "sonar.sso.enable";

  private static final String LOGIN_HEADER_PARAM = "sonar.sso.loginHeader";
  private static final String LOGIN_HEADER_DEFAULT_VALUE = "X-Forwarded-User";

  private static final String NAME_HEADER_PARAM = "sonar.sso.nameHeader";
  
  private static final String EMAIL_HEADER_PARAM = "sonar.sso.emailHeader";

  private static final String GROUPS_HEADER_PARAM = "sonar.sso.groupsHeader";
  private static final String SSO_SUB_CAT = "SSO";
  private static final String NAME_HEADER__DEFAULT_VALUE = "X-Forwarded-Name";
  private static final String EMAIL_HEADER_DEFAULT_VALUE = "X-Forwarded-Email";
  private static final String GROUPS_HEADER_DEFAULT_VALUE = "X-Forwarded-Groups";

  private final Settings settings;
  private final UserIdentityAuthenticator userIdentityAuthenticator;
  private final JwtHttpHandler jwtHttpHandler;

  public SsoAuthenticator(Settings settings, UserIdentityAuthenticator userIdentityAuthenticator, JwtHttpHandler jwtHttpHandler) {
    this.settings = settings;
    this.userIdentityAuthenticator = userIdentityAuthenticator;
    this.jwtHttpHandler = jwtHttpHandler;
  }

  public Optional<UserDto> authenticate(HttpServletRequest request, HttpServletResponse response) {
    if (!settings.getBoolean(ENABLE_PARAM)) {
      return Optional.empty();
    }
    String login = getHeaderValue(request, LOGIN_HEADER_PARAM);
    if (login == null) {
      return Optional.empty();
    }
    UserDto userDto = doAuthenticate(request, login);

    Optional<UserDto> userFromToken = jwtHttpHandler.validateToken(request, response);
    if (userFromToken.isPresent() && userDto.getLogin().equals(userFromToken.get().getLogin())) {
      // User is already authenticated
      return userFromToken;
    }
    jwtHttpHandler.generateToken(userDto, request, response);
    return Optional.of(userDto);
  }

  private UserDto doAuthenticate(HttpServletRequest request, String login) {
    String name = getHeaderValue(request, NAME_HEADER_PARAM);
    String email = getHeaderValue(request, EMAIL_HEADER_PARAM);
    UserIdentity.Builder userIdentityBuilder = UserIdentity.builder()
      .setLogin(login)
      .setName(name == null ? login : name)
      .setEmail(email)
      .setProviderLogin(login);
    String groupsValue = getHeaderValue(request, GROUPS_HEADER_PARAM);
    if (groupsValue != null) {
      userIdentityBuilder.setGroups(new HashSet<>(COMA_SPLITTER.splitToList(groupsValue)));
    }
    return userIdentityAuthenticator.authenticate(userIdentityBuilder.build(), new SsoIdentityProvider());
  }

  @CheckForNull
  private String getHeaderValue(HttpServletRequest request, String settingKey) {
    String settingValue = settings.getString(settingKey);
    if (!isEmpty(settingValue)) {
      return trimToNull(request.getHeader(settingValue));
    }
    return null;
  }

  private static class SsoIdentityProvider implements IdentityProvider {
    @Override
    public String getKey() {
      // TODO should it be 'SSO' ? Or given by a setting ?
      return SQ_AUTHORITY;
    }

    @Override
    public String getName() {
      return getKey();
    }

    @Override
    public Display getDisplay() {
      return null;
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    @Override
    public boolean allowsUsersToSignUp() {
      return true;
    }
  }

  // TODO settings should come from sonar.properties
  public static List<PropertyDefinition> definitions() {
    return ImmutableList.of(
      PropertyDefinition.builder(ENABLE_PARAM)
        .type(BOOLEAN)
        .defaultValue("false")
        .description("Enable SSO authentication by using HTTP header.")
        .category(CATEGORY_SECURITY)
        .subCategory(SSO_SUB_CAT)
        .build(),
      PropertyDefinition.builder(LOGIN_HEADER_PARAM)
        .defaultValue(LOGIN_HEADER_DEFAULT_VALUE)
        .description("Mandatory header value that contains the user login")
        .category(CATEGORY_SECURITY)
        .subCategory(SSO_SUB_CAT)
        .build(),
      PropertyDefinition.builder(NAME_HEADER_PARAM)
        .defaultValue(NAME_HEADER__DEFAULT_VALUE)
        .description("Optional header value that contains the user name")
        .category(CATEGORY_SECURITY)
        .subCategory(SSO_SUB_CAT)
        .build(),
      PropertyDefinition.builder(EMAIL_HEADER_PARAM)
        .defaultValue(EMAIL_HEADER_DEFAULT_VALUE)
        .description("Optional header value that contains the user email")
        .category(CATEGORY_SECURITY)
        .subCategory(SSO_SUB_CAT)
        .build(),
      PropertyDefinition.builder(GROUPS_HEADER_PARAM)
        .defaultValue(GROUPS_HEADER_DEFAULT_VALUE)
        .description("Optional header value that will contains the list of groups, separated by coma")
        .category(CATEGORY_SECURITY)
        .subCategory(SSO_SUB_CAT)
        .build());
  }

}
