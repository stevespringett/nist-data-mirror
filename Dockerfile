FROM nginx:1.13.9
LABEL Author: Sam Dammers <sam.dammers@versent.com.au>

# Remove the default Nginx configuration file
RUN rm -v /etc/nginx/nginx.conf

# Copy a configuration file from the current directory
ADD nginx.conf /etc/nginx/

# Copy NIST Datafiles
ADD nist /usr/share/nginx/html/
ADD nginx_hosted_files /usr/share/nginx/html/
RUN rm /usr/share/nginx/html/index.html
# Append "daemon off;" to the beginning of the configuration
RUN echo "daemon off;" >> /etc/nginx/nginx.conf

# Expose ports
EXPOSE 8080

# Set the default command to execute
# when creating a new container
CMD service nginx start