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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.MapSettings;
import org.sonar.api.config.Settings;
import org.sonar.db.user.UserDto;

import static org.junit.rules.ExpectedException.none;
import static org.mockito.Mockito.mock;
import static org.sonar.db.user.UserTesting.newUserDto;

public class SsoAuthenticatorTest {

  @Rule
  public ExpectedException expectedException = none();

  static final String LOGIN = "LOGIN";
  static final String PASSWORD = "PASSWORD";

  static final UserDto USER = newUserDto();

  Settings settings = new MapSettings();

  UserIdentityAuthenticator userIdentityAuthenticator = mock(UserIdentityAuthenticator.class);

  HttpServletRequest request = mock(HttpServletRequest.class);
  HttpServletResponse response = mock(HttpServletResponse.class);
  JwtHttpHandler jwtHttpHandler = mock(JwtHttpHandler.class);

  SsoAuthenticator underTest = new SsoAuthenticator(settings, userIdentityAuthenticator, jwtHttpHandler);

  @Test
  public void authenticate() throws Exception {

  }

}
