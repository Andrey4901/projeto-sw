#
# Cria uma imagem Docker com o MCP Inspector (https://modelcontextprotocol.io/docs/2026-07-28/tools/inspector)
# o servidor MCP Java criado neste projeto
#
# imagem base Node v22
FROM node:22-bookworm-slim
# copia da imagem Eclipse Temurin 25 (https://adoptium.net/pt-BR/temurin) o
# conteúdo do diretório "/opt/java/openjdk" para o mesmo diretório na imagem
# que está sendo criada 
COPY --from=eclipse-temurin:25-jre /opt/java/openjdk /opt/java/openjdk
# configurações do Java na imagem sendo criada
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
# configurações para o Node na imagem sendo criada
ENV NODE_ENV=production
ENV HOME=/home/node
# configurações para o MCP Inspector
ENV HOST=0.0.0.0
ENV DANGEROUSLY_BIND_ALL_INTERFACES=true
ENV CLIENT_PORT=6274
ENV MCP_SANDBOX_PORT=6275
ENV MCP_APP_ORIGIN_PORT=6278
ENV MCP_AUTO_OPEN_ENABLED=false
ENV MCP_INSPECTOR_SECRET_STORE=memory
# diretório de trabalho na imagen sendo criada
WORKDIR /home/node
# 'instala' o MCP Inspector na imagem sendo criada
RUN npm install --global @modelcontextprotocol/inspector \
    && mkdir -p /home/node/.mcp-inspector \
    && chown -R node:node /home/node/.mcp-inspector
# usuário sem privilégios que executará a aplicação (MCP Inspector)
USER node
# solicita ao Docker que exponha algumas portas
EXPOSE 6274
EXPOSE 6275
EXPOSE 6278
# define o comando a ser executado assim que o contêiner for iniciado
ENTRYPOINT ["mcp-inspector"]
# argumento para o comando acima
CMD ["--web"]
