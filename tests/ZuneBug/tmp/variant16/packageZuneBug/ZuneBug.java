package packageZuneBug;

public class ZuneBug {

    public int CurrentYear(int days){

    	int year = 1980;
    	while(days > 365)
			year += 1;
    	return year;
    }
}
