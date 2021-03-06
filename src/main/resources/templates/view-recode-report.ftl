<#include "template-header.ftl">
<#include "template-footer.ftl">
<@header/>
<script src="/js/view-recode.js"></script>
<#if isBackup ?? && isBackup>
<div class="row-fluid" style="margin-top:200px;">
	<div class="span12">
		<center>The coded data is being backed up now, please be patient!</center>
	</div>
</div>
<script>
	$(document).ready(function(){
		setTimeout(function(){
			location.reload();
		}, 5000);
	});
</script>
<#elseif isUploading ?? && isUploading>
<div class="row-fluid" style="margin-top:200px;">
	<div class="span12">
		<center>The file is being uploaded now, please be patient!</center>
		<br><br>
	</div>
</div>
<div class="row-fluid">
	<div class="offset3 span6">
		<div class="progress progress-striped active">
		  <div class="bar" style="width:${percentage}%;"></div>
		</div>
	</div>
</div>
<script>
	$(document).ready(function(){
		setTimeout(function(){
			location.reload();
		}, 5000);
	});
</script>
<#elseif hidForm?? && !hidForm>
<form method="POST" enctype="multipart/form-data">
	<div class="row-fluid">
		<div class="span12">
		<center><h3>Recode data</h3></center>
		</div>
	</div><br><br>
	<div id="backup-check" class="row-fluid"></div>
	<div class="row-fluid">
		<div class="offset3 span6 well">
			<div class="row-fluid">
				<strong>Select a code system : </strong>
				<div style="float:right;">
					<select id="selectedCodeSystem" name="selectedCodeSystem"></select>
				</div>
			</div><br>
			<div class="row-fluid">
				<strong>Define the name of the coding job :</strong>
				<input type="text" name="codingJobName" style="float:right;margin-bottom:-5px;"/> 
			</div>
		</div>
	</div>
	<div class="row-fluid">
		<div class="span12">
			<center>Upload a dataset that needs to be recoded in <u>csv</u> format. Please include individual identifiers in dataset for reference.</center>
		</div>
	</div><br>
	<div class="row-fluid">
		<div class="span12">
			<center>
				<div class="fileupload fileupload-new" data-provides="fileupload">
					<div class="input-append">
						<div class="uneditable-input">
							<i class="icon-file fileupload-exists"></i>
							<span class="fileupload-preview"></span>
						</div>
						<span class="btn btn-file btn-info">
							<span class="fileupload-new">Select file</span>
							<span class="fileupload-exists">Change</span>
							<input type="file" name="file" required/>
						</span>
						<a href="#" class="btn btn-danger fileupload-exists" data-dismiss="fileupload">Remove</a>
						<button id="upload-button" class="btn btn-primary" type="button">Upload</button>
					</div>
				</div>
			</center>
		</div>
	</div>
</form>
<div class="row-fluid">
	<div class="offset3 span6">
		<#if message ??><div class="alert alert-error"><strong>Error : </strong> ${message} <button type="button" class="close" data-dismiss="alert">&times;</button> </div> </#if>
		<table class="table table-bordered">
			<thead>
				<tr>
					<th class="equal-width-th">Name</th>
					<th class="equal-width-th">Identifier</th>
				</tr>
			</thead>
			<tbody>
				<tr>
					<td>MOUNTAINBIKE</td>
					<td>individual_1</td>
				</tr>
				<tr>
					<td>WIELRENNEN</td>
					<td>individual_2</td>
				</tr>
				<tr>
					<td>RACEFIETS</td>
					<td>individual_3</td>
				</tr>
				<tr>
					<td>SPINNEN</td>
					<td>individual_4</td>
				</tr>
			</tbody>
		</table>
	</div>
</div>
<script>
	$(document).ready(function(){
		$('#upload-button').click(function(event){
			$('form:first').attr('action','/recode/upload').submit();
		});
		molgenis.findAllCodeSystems(function(data){
			var select = $('#selectedCodeSystem');
			$.each(data.results, function(index, hit){
				select.append('<option value="' + hit.columnValueMap.name + '">' + hit.columnValueMap.name + '</option>');
			});
		});
		molgenis.checkBackup($('#backup-check'));
	});
</script>
<#else>
<form method="GET" enctype="multipart/form-data">
	<input id="selectedCodeSystem" name="selectedCodeSystem" type="hidden"
	<div class="row-fluid">
		<div class="span12">
		<center><h3>Report for recoding</h3></center>
		</div>
		<br><br><br>
	</div>
	<div class="row-fluid">
		<div class="offset3 span6">
			<div class="row-fluid">
				<div class="span6">
					Current threshold : ${threshold}%
				</div>
				<div class="span6">
					<input name="threshold" type="text" style="width:50px;float:right;"/>
					<button id="update-threshold-button" class="btn btn-float-right" type="button">Update</button>
				</div>
			</div>
		</div>
	</div>
	<div class="row-fluid">
		<div class="offset3 span6 well">
			<div id="total-container" class="row-fluid">
				<div class="span8">
					The total number of individuals is <strong><span>${totalNumber}</span></strong>
				</div>
			</div><br>
			<div id="matched-container" class="row-fluid">
				<div class="span8">
					The total number of unique matched items is <strong><span id="total-matched"></span></strong>
				</div>
			</div><br>
			<div id="unmatched-container" class="row-fluid">
				<div class="span8">
					The total number of unique 	unmatched items is <strong><span id="total-unmatched"></span></strong>
				</div>
			</div><br><br>
			<div class="row-fluid">
				<button id="option-button" class="btn btn-primary" type="button">Options</button>
			</div>
			<div class="row-fluid" id="option-div" style="display:none;">
				<br><legend></legend>
				<button id="download-button" class="btn btn-inverse" type="button">Download</button>
				<button id="finished-button" class="btn" type="button">Finish recoding</button>
				<button id="backup-button" class="btn" type="button" style="float:right;">Backup data	</button>
				<br><br><legend></legend>
				Upload missing values
				<div class="fileupload fileupload-new" data-provides="fileupload">
					<div class="input-append">
						<div class="uneditable-input">
							<i class="icon-file fileupload-exists"></i>
							<span class="fileupload-preview"></span>
						</div>
						<span class="btn btn-file btn-info">
							<span class="fileupload-new">Select file</span>
							<span class="fileupload-exists">Change</span>
							<input type="file" name="file" required/>
						</span>
						<a href="#" class="btn btn-danger fileupload-exists" data-dismiss="fileupload">Remove</a>
						<button id="upload-button" class="btn btn-primary" type="button">Upload</button>
					</div>
				</div>
			</div>
		</div>
	</div>
	<div class="row-fluid">
		<div id="result-container" class="offset2 span8"></div>
	</div>
</form>
<script>
	$(document).ready(function(){
		molgenis.retrieveTotalNumber(function(data){
			$('#total-matched').append(data.matchedTotal ? data.matchedTotal : 0);
			$('#total-unmatched').append(data.unmatchedTotal ? data.unmatchedTotal : 0);
		});
		molgenis.retrieveResult($('#matched-container'), $('#result-container'), '${selectedCodeSystem}', true, false);
		molgenis.retrieveResult($('#unmatched-container'), $('#result-container'), '${selectedCodeSystem}', false, true);
		
		$('#option-button').click(function(){
			if($('#option-div').is(':hidden')){
				$('#option-div').show();
			}else{
				$('#option-div').hide();
			}
		});
		
		$('#finished-button').click(function(){
			$('form:first').attr({
				'method' : 'GET',
				'action' : '/recode/finish'
			}).submit();
		});
		$('#download-button').click(function(){
			$('form:first').attr({
				'method' : 'GET',
				'action' : '/recode/download'
			}).submit();
		});
		$('#update-threshold-button').click(function(){
			$('form:first').attr({
				'method' : 'POST',
				'action' : '/recode/threshold/'
			}).submit();
		});
		$('#backup-button').click(function(){
			$('form:first').attr({
				'method' : 'GET',
				'action' : '/recode/backup/'
			}).submit();
		});
		$('#upload-button').click(function(event){
			$('form:first').attr({
				'method' : 'POST',
				'action' : '/recode/upload/missing'
			}).submit();
		});
	});
</script>
</#if>
<@footer/>