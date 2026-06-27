FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY backend ./backend

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl ca-certificates \
  && rm -rf /var/lib/apt/lists/*

RUN cd backend \
  && mkdir -p lib out \
  && curl -L -o lib/postgresql-42.7.11.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.11/postgresql-42.7.11.jar \
  && curl -L -o lib/mysql-connector-j-9.7.0.jar https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar \
  && curl -L -o lib/mssql-jdbc-13.4.0.jre11.jar https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/13.4.0.jre11/mssql-jdbc-13.4.0.jre11.jar \
  && javac -cp "lib/*" -d out $(find src -name '*.java' | sort)

WORKDIR /app/backend

CMD ["java", "-cp", "out:lib/*", "com.askdb.Main"]
