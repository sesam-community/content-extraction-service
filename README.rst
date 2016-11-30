===============================
Content extraction microservice
===============================

A java micro service for transforming a JSON entity stream. This service is designed to be used as a microservice system with
the `HTTP transform <https://docs.sesam.io/configuration.html#the-http-transform>`_ in a Sesam service instance.

It downloads files via a given URL and extracts text information from the downloaded content using the Apache Tika library
(http://tika.apache.org/). The resulting text is inserted as a string property into the entities and returned to the POSTer.

You will need a working Java 8 dev environment and Maven 3.x to build this service.

Running in Docker
-----------------

::

   cd content-extraction-service
   mvn clean install
   docker build -t sesam/content-extraction-service:latest .
   docker run -it -p 4567:4567 sesam/content-extraction-service:latest  
  
Get the IP from docker:

::

  docker inspect -f '{{.Name}} - {{.NetworkSettings.IPAddress }}' content-extraction-service

Example
-------
  
JSON entities can be posted to 'http://localhost:4567/transform'. The result is streamed back to the client. Exchange "localhost" with the Docker IP if running in Docker.

::

   $ curl -s -XPOST 'http://localhost:4567/transform' -H "Content-type: application/json" -d '[{ "_id": "jane", "url": "http://some-url-to-a-file/file.pdf"}]' | jq -S .
   [
     {
       "_id": "jane",
       "url": "http://some-url-to-a-file/file.pdf",
       "_content": "The content in text form"
     }
   ]

Note the example uses `curl <https://curl.haxx.se/>`_ to send the request and `jq <https://stedolan.github.io/jq/>`_ prettify the response.

Configuration
-------------

You can configure the service with the following environment variables:

======================  =====================================================================================   ===========
Variable                Description                                                                             Default

``SOURCE_PROPERTY``     The name of the property holding the source url.                                        "url"

``TARGET_PROPERTY``     The name of the property that will hold the extracted content.                          "_content"

``USERNAME``            Username for authentication needed to download the file represented by the              null
                        ``SOURCE_PROPERTY``. If not set or null, no authentication will be attempted.

``PASSWORD``            The password for the ``USERNAME``. Only applicable if ``USERNAME`` is set.              null

``AUTH_TYPE``           The authentication method to use if ``USERNAME`` and ``PASSWORD`` is both set.          "basic"
                        This is an enum with the following valid values (case sensitive): "basic",
                        "digest" and "ntlm". If "ntlm" is specified, you can provide additional information
                        in ``WORKSTATION`` and/or ``DOMAIN``.

``WORKSTATION``         Used in conjuction with "ntlm" as ``AUTH_TYPE``                                         null

``DOMAIN``              Used in conjuction with "ntlm" as ``AUTH_TYPE``                                         null

``SOCKET_TIMEOUT``      TCP socket timeout value in seconds.                                                    "120" 

``CONNECTION_TIMEOUT``  Connection timout value in seconds.                                                     "10"

``TRUST_EVERYTHING``    Disable TLS certificate and hostname verification. Note that this is insecure.          false

``THREADS``             The number of worker threads to use for content download and extraction.                "8"
                        Note that increasing this value will also increase memory and CPU usage. If the
                        value set increases resource usage beyond the capactity of the machine running the
                        service the service process may be terminated.
======================  =====================================================================================   ===========


When running in Docker you can either specify this in a file (see https://docs.docker.com/compose/env-file/) or on the command line with "docker run .. -e VAR1=VAL1 -e VAR2=VAL2 .."
