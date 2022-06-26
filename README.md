Leihs - Switch OIDC - Authentication System
===========================================

This project contains code and deployment recipes to use [SWITCH
edu-ID](https://www.switch.ch/edu-id/) via [OpenID
Connect](https://www.switch.ch/edu-id/docs/services/openid-connect/) as an
external authentication system for [leihs](https://github.com/leihs).



Ad-Hoc Sign-up and User Management
----------------------------------

This authenticator supports ad hoc user and group managemend via the leihs
admin API.

Note that this is is much more limited than a full import and synchronisation
against an directory service as provided with
https://github.com/functional-swiss/leihs-sync for example.


### Limitations of Ad-Hoc User Management


* Only a very limited number of attributes, namely: the name, the given
  name and an email-address can currently be imported via Switch.

* As an user profile is only ever updated during sign-in via this particular
  system the attributes might become an remain stale. The do not reflect the
  current state in the home organization.

* Accounts will not be removed, disabled, or even anonymized after the user
  retires from their home organization. Such tasks must be handled via other
  means.

* Affiliations for the defined home organization will be mapped to groups. That
  means that only a very limited number of groups will be available. Usually
  the distinction between students and staff is supplied via affiliations.

* Group membership might not reflect the current state for the same reason
  account properties might become stale.

* Only at most one home organization can be bound to one instance of this
  authentication system for the purpose of ad hoc sign-up.



Configuation
------------

### Switch OIDC Authentication Configuration

TODO

### Ad-Hoc User management Configuration


TODO




TODO
----

* Enable removal from affiliation groups, needs leihs upgrade.


Development Notes
-----------------

Test URL https://test.home.arpa:3200/authenticators/switch-open-id/{{CUSTOMER}}/protected/
Test E-Mail Address: thomas.schank.test@{{CUSTOMER}}.ch


### Start in dev mode

    ./bin/clj-run run -c proto-config/cust-secret-config.yml

### Add Hostname to MacOS

As root, e.g. `sudo -i -u root`:

* add `127.0.0.1 test.home.arpa` to the `/etc/hosts` file
* flush DNS cache `killall -HUP mDNSResponder`


### mod_auth_openidc via Docker

There is an configuration and docker setup for `mod_auth_openidc` provided with
this repository. This is for testing, prototyping, debugging and so on. We
don't use it in production! See also `./bin/docker-build` and
`./bin/docker-run`.


### References

#### Switch


* Resource Listing (hard to find): https://rr.aai.switch.ch/menu_res_options.php
* https://login.eduid.ch/.well-known/openid-configuration
* https://www.switch.ch/edu-id/docs/services/openid-connect/


#### OpenID Connect in General

Simple and complete code flow:
https://connect2id.com/learn/openid-connect#example-auth-code-flow-step-1

https://developers.onelogin.com/openid-connect/guides/auth-flow-pkce

https://auth0.com/docs/get-started/authentication-and-authorization-flow/authorization-code-flow


