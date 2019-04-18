import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;




import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.UUID;

public class LogEvent implements RequestHandler<SNSEvent, Object> {
    private DynamoDB dynamoDB;
    private Regions REGION = Regions.US_EAST_1;
    protected static String token;
    protected static String app_username;
    protected static String SES_FROM_ADDRESS;
    protected static final String EMAIL_SUBJECT = "Forget password reset link";
    protected static String HTMLBODY;
    protected static String TEXTBODY;

    public void initDynamoDbClient(Context context){
        AmazonDynamoDB client = AmazonDynamoDBAsyncClientBuilder.standard().build();
        context.getLogger().log("create DynamoDB client via Builder");
        context.getLogger().log(("DynamoDB client" + client.toString()));
        this.dynamoDB = new DynamoDB(client);


    }

    public Object handleRequest(SNSEvent request, Context context){
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation started: " + timeStamp);
        context.getLogger().log("Is the request NULL: " + (request == null));
        context.getLogger().log("record size: " + request.getRecords().size());
        context.getLogger().log("--------------Task 1 complete--------------");

        // Execution
        app_username = request.getRecords().get(0).getSNS().getMessage();
        context.getLogger().log(app_username+" request reset password");
        token = UUID.randomUUID().toString();
        context.getLogger().log("token: "+ token);

        // connect to AWS DynamoDB
        this.initDynamoDbClient(context);
        context.getLogger().log("DynamoDB client build successfully");
        String DBTableName = System.getenv("DynamoDB_TableName");
        context.getLogger().log("DynamoDB Table name is: " + DBTableName);
        SES_FROM_ADDRESS = System.getenv("From_EmailAddress");

        Table tableInstance = dynamoDB.getTable(DBTableName);
        if( tableInstance  != null){
            context.getLogger().log("Get Table from DynamoDB: " + DBTableName);
        }else return null;
        String domain = SES_FROM_ADDRESS.substring(SES_FROM_ADDRESS.indexOf("@")+1);
        if((tableInstance.getItem("id", app_username)) == null){
            TEXTBODY = "https://" + domain+ "/reset?email=" + app_username + "&token= " + token;
            HTMLBODY = "<h3>You have successfully requested an Password Reset using Amazon SES!</h3>"
                    + "<p>Please reset the password using the below link in 20 minutes.<br/> " +
                    "Link:https://"+ domain+"/reset?email=" + app_username + "&token= " + token+"</p>";

            context.getLogger().log("Textbody : "+ TEXTBODY);
            context.getLogger().log("Htmlbody : "+ HTMLBODY);
            context.getLogger().log("--------------Task2 complete--------------");
            try{
                AmazonSimpleEmailService amazonSimpleEmailService = AmazonSimpleEmailServiceClientBuilder.standard()
                        .withRegion(REGION).build();
                context.getLogger().log("--------------Task3 SES build complete--------------");

                SendEmailRequest emailRequest = new SendEmailRequest().withDestination(new Destination().withToAddresses(app_username))
                        .withMessage( new Message()
                                .withBody( new Body()
                                        .withHtml(new Content()
                                                .withCharset("UTF-8").withData(HTMLBODY))
                                        .withText(new Content()
                                                .withCharset("UTF-8").withData(TEXTBODY)))
                                .withSubject(new Content()
                                        .withCharset("UTF-8").withData(EMAIL_SUBJECT)))
                        .withSource(SES_FROM_ADDRESS);
                amazonSimpleEmailService.sendEmail( emailRequest);
                context.getLogger().log("--------------Task 4 send email complete--------------");
                System.out.println("Email sent successfully!");
            } catch(Exception ex){
                System.out.println("The email wasn't send, Error message: " + ex.getMessage());
                context.getLogger().log("Task 5");
                return null;
            }

            context.getLogger().log("User's reset password request doesn't exist in the DynamoDb, " +
                    " create new token and send an email to user");
            Number TTL = System.currentTimeMillis() /1000L + 1200;
            context.getLogger().log("token valid time: " + TTL);
            this.dynamoDB.getTable(DBTableName).putItem(
                    new PutItemSpec().withItem( new Item().withString("id",app_username)
                    .withString("token",token)
                    ));


        }else{
            context.getLogger().log("User's request already exist in dynamoDB");
        }

        timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation completed: " + timeStamp);
        return null;
    }
}
